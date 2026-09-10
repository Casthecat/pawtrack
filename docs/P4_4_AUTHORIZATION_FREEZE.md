# P4 authorization freeze — P4.4 quality report

Verified 2026-09-10. **P4 is frozen at the implementation and evidence boundary recorded here.** No later product phase is started. Existing uncommitted P4.2/P4.3 work was preserved; no commit, Git tag, deployment or normal development-database migration was performed by P4.4.

## 1. Final route inventory

The [complete final inventory](P4_4_ROUTE_INVENTORY.md) re-records all eight controllers, auth filters, WebConfig media/CORS, actuator and developer documentation. GET classifications also cover HEAD.

- PUBLIC: Cat gallery, detail and dashboard; `/uploads/**` portraits; `/actuator/health`; auth CSRF initialization and POST login/logout lifecycle with mandatory CSRF.
- AUTHENTICATED: `/api/auth/me` and receipt GET `/api/adoptions/{id}`, followed by the unchanged service-level STAFF-or-owner check.
- ADOPTER: POST `/api/adoptions`, GET `/api/me/adoptions`.
- STAFF: review queue and approve/reject, global alert queue, both Cat alert aliases, health history/write, care timeline, resolution, Cat creation, portrait upload and developer API docs.
- DENIED: remaining `/api` and `/api/**` requests, including unclassified methods and paths. SPA/static routes retain the non-API public fallback.

The two alert aliases `/api/cats/{id}/alerts` and `/api/cats/{id}/health/alerts` remain STAFF-only. Public dashboard scalar health information is distinct from private care notes/history. The final inventory supersedes prior phase classifications; older reports remain historical records.

## 2. Deny-by-default implementation

SessionSecurityConfiguration now explicitly authorizes supported HTTP methods and paths, then applies `denyAll()` to `/api` and `/api/**` before non-API fallback handling. Public Cat reads are not an unrestricted `/api/cats/**` rule. Numeric entity-path constraints prevent a newly added literal route such as `/api/cats/private-probe` from inheriting a public entity read matcher.

STAFF is not a superuser bypass for unclassified APIs. Known routes retain their role/object checks. Normal denied anonymous API requests return JSON 401; authenticated denial returns JSON 403; non-owner adopter receipt reads retain normal JSON 404. CSRF enforcement is unchanged. Allowed-origin CORS preflight is handled before authorization and does not grant the subsequent business request permission.

Removed `/api/cats/{id}/status` and `/api/alerts/{id}/close` now produce STAFF 403 at the security boundary rather than handler-level 404. Their regression expectations were updated while retaining the assertions that retired paths cannot mutate business state. Non-numeric entity paths and unsupported methods also fall outside the explicit allowlist.

## 3. Proof of unclassified API denial

ApiDefaultDenyIntegrationTest imports a test-only controller exposing real GET/POST probe handlers under `/api/unclassified-probe`, `/api/cats/private-probe` and `/api/auth/private-probe`.

Three role cases cover anonymous, ADOPTER and STAFF. All probe calls are denied (401/403); POST supplies valid CSRF, so the result is an authorization denial rather than an earlier token failure. The probe invocation counter stays zero. A non-API shell probe and anonymous Cat gallery still return 200. TestComponent prevents the probe controller from being scanned into the production application or unrelated contexts.

## 4. Image-upload findings and hardening

Before P4.4, the implementation accepted any claimed `image/*`, copied attacker bytes directly, used a timestamp plus the client-provided extension, had no application content/size check, and returned a path derived from the physical upload setting. SVG or HTML disguised as an image could therefore cross a same-origin file boundary. STAFF authorization alone did not validate content.

The existing operation now delegates to a small CatImageStorage component using Java ImageIO:

- Accept JPEG and PNG only; verify the decoder's actual format and its agreement with the declared MIME type. SVG, HTML, JavaScript, unsupported image formats and malformed/disguised inputs are rejected.
- Limit input to 5 MiB, enforced both by multipart configuration and a bounded service read. The multipart request limit is 6 MiB to allow envelope overhead.
- Before decoding pixels, limit each dimension to 4096 and the total to 16 million pixels.
- Decode and re-encode pixels, so the published file is not a raw copy of attacker content, original metadata or appended script bytes.
- Ignore the original filename completely. Generate a UUID with `.jpg` or `.png` from the verified format.
- Normalize the configured directory, resolve its actual filesystem location, and require the generated target to be its direct child. CREATE_NEW prevents overwriting an existing filename; error cleanup only deletes a file created by this operation.
- Return `uploads/{uuid}.{extension}`, independent of the physical directory. Public GET/HEAD retrieval and the existing security `nosniff` header remain.
- Return understandable JSON 400 for invalid input and 413 for excessive byte size. Multipart size exceptions use the same JSON error style.

CatService retains the original Cat lock and surrounding upload transaction. WebConfig now ensures a trailing slash on the resource-directory URI even before that directory exists, fixing first-upload retrieval with a custom/new directory.

No media service, antivirus, object storage or CDN was added. Filesystem writes and SQL commits are still separate resources: a later database commit failure can leave an orphaned image, and replaced/old files are not automatically collected. Existing files placed outside this upload operation are not retroactively sanitized. The upload directory remains an application-controlled local storage boundary.

## 5. Upload security tests

CatImageSecurityIntegrationTest adds **13 executed cases** covering valid JPEG/PNG, server-generated unique names, public retrieval/MIME/nosniff, anonymous denial, ADOPTER denial with valid CSRF, STAFF missing-CSRF denial, SVG/HTML/JavaScript/GIF rejection, disguised non-image data, MIME mismatch, truncated/empty content, Unix/Windows/absolute path-like filenames, oversized byte and pixel inputs, and removal of appended script bytes by re-encoding.

The test uses a dedicated upload root that does not exist when resource routing is configured, proving first-write public retrieval as well as containment. Rejected uploads do not assign a Cat image URL. Existing CatImageUploadIntegrationTest and the real-session StaffAuthorizationIntegrationTest now use genuine encoded image fixtures instead of arbitrary bytes labelled JPEG. No staff role boundary was relaxed to make upload tests pass.

## 6. Demo-account isolation design

DemoAccounts retains `@Profile("demo")`. A static BeanFactoryPostProcessor rejects unsafe demo configuration before DataSource/JPA initialization. Accepted configuration requires:

- A named `jdbc:h2:mem:` URL with a simple alphanumeric/underscore/hyphen database name, optionally followed by `;DB_CLOSE_DELAY=-1`.
- `org.h2.Driver`.
- `spring.jpa.hibernate.ddl-auto=create-drop`.

Persistent file/PostgreSQL URLs and extra URL directives such as INIT are rejected. This early check prevents a misconfigured demo's create-drop setting from reaching a persistent database through the normal datasource configuration.

Before any account repository lookup/provisioning, the seeder also reads actual JDBC metadata and requires product H2 and the expected in-memory database URL. A configuration label alone does not authorize seeding. The check runs before other demo runners. Existing account fixtures remain one STAFF and two ADOPTER accounts with encoded passwords; no credentials are exposed by a new API and no account-management framework was added.

## 7. Demo safety tests

DemoAccountSafetyTest adds **7 executed cases**:

- Four profile cases (default, test, postgres-migration, postgres-runtime) verify the demo seeder bean is absent and provisioning collaborators are never called.
- A full application startup attempt uses the demo profile against a deliberately isolated file-backed H2 database. Startup fails before JPA; its pre-existing sentinel account row survives unchanged and no known-password row is added. This fixture never targets normal development data.
- PostgreSQL-style and H2 INIT URLs are rejected in lightweight contexts without constructing or connecting a datasource.

The existing full DemoAccountsIntegrationTest proves ordinary demo H2 startup creates exactly the intended three distinct fixtures and encoded passwords. IdentitySessionIntegrationTest and real PostgreSQL identity migration tests retain non-demo empty-account assertions. The final live runner also starts the normal disposable demo successfully under the new guard.

## 8. PostgreSQL same-owner submission concurrency

Added one case to the existing CatLockPostgresRuntimeIT. It pre-creates a real adopter account, then submits twice for the same Cat through AdoptionService using the same principal.

The existing coordinator holds the Cat lock in one transaction, observes the second transaction blocked using `pg_blocking_pids`, and verifies it cannot finish before release. Final outcome is one service success and one 409, with exactly one PENDING application for that Cat/account and one application row overall. The coordinator uses 200 as its internal success marker; the normal HTTP submission endpoint still returns 201.

All four PostgreSQL runtime cases passed. The original Cat lock implementation, approval transaction, resolution transaction and health logic were not changed. This is evidence for four specific coordinated histories, not throughput, load capacity or all possible interleavings.

## 9. Private-data cache/account-switch result

The existing React Query architecture was retained. The focused browser regression was strengthened so owner A loads both the receipt and My applications list before logout, then owner B logs in. B cannot display A's receipt, sees the inaccessible state and gets an empty own list. Desktop and narrow executions pass.

Existing account-scoped query keys and login/logout/401 cache clearing provide the behavior. No frontend production state architecture or ownership rule changed. Other existing cases continue to verify session-expiry recovery, STAFF receipt access, adopter workspace denial and anonymous login requirements.

## 10. Backend full-suite result

`mvn clean test`: **150 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**. This preserves the previous 127 tests and adds 23 hardening cases: 13 upload, 7 demo safety and 3 default-denial cases. Final log: `backend/p4-4-test.log`.

Initial regression failures identified obsolete 404 expectations for removed APIs and a missing storage dependency in an MVC test slice. Those fixtures were adapted to the new explicit boundary; business assertions were not replaced by security-only assertions. The complete suite was rerun successfully after the fixes.

## 11. Frontend build, lint and browser results

- `npm run build`: passed.
- `npm run lint`: passed.
- `npm run test:care`: **34 passed**, no retries, at desktop and narrow sizes.

Logs: `frontend/p4-4-build.log`, `p4-4-lint.log`, `p4-4-care.log`. P4.4 changes only the cache regression in frontend test source; no product page, client authentication mechanism or business UI was rewritten.

## 12. PostgreSQL migration/runtime results

All migration tests through V10: **12 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**. Executed PostgresMigrationIT, CareRecordPostgresMigrationIT, AlertResolutionPostgresMigrationIT, CatStatusPostgresMigrationIT, RemoveLegacyCatStatusPostgresMigrationIT, UserAccountPostgresMigrationIT and AdoptionOwnershipPostgresMigrationIT.

CatLockPostgresRuntimeIT: **4 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**, including the new same-owner case.

Logs: `backend/p4-4-migrations.log`, `backend/p4-4-runtime.log`. Tests use UUID schemas, validate current Hibernate mappings and clean their own schemas. V1–V10 were not changed; no V11 exists. Normal development data was not migrated or used for demo safety fixtures.

## 13. Live session/ownership demo result

`npm run test:live`: **1 passed**, no retries, using the existing real backend/session/CSRF flow with no API interception.

It demonstrates anonymous browsing, ADOPTER login/submission/My applications/owned receipt, a second adopter's receipt denial, anonymous receipt 401, STAFF review/approval and Nori care resolution, ADOPTER staff denial, logout and updated owner receipt/list outcomes. Existing desktop/narrow care evidence remains part of this test.

Log: `frontend/p4-4-live.log`. Local generated screenshots are under `frontend/test-results/live/milestone-real-demo-comple-f8d87-on-and-care-vertical-slices/`, including the authenticated apply form, narrow My applications, open/closed care and adopted-under-observation views. Logs/screenshots are generated evidence rather than source changes.

## 14. Guarantees supported after P4

P4 now implements server-session authentication, session-fixation protection, CSRF, STAFF operation authorization, ADOPTER submission authorization, account-ID application ownership, owner-only My applications, API deny-by-default, validated local JPEG/PNG upload and fail-closed known-password demo provisioning.

Anonymous protected API denial, authenticated role denial and non-owner receipt 404 are distinguished. Historical NULL-owner rows remain staff-only and are not claimed through matching email. Default-denial probes cannot execute even as STAFF. New image uploads cannot choose filesystem names or publish raw SVG/HTML through MIME spoofing. Misapplied persistent demo datasource configuration does not provision known credentials.

The final route inventory is the maintenance contract: new API methods/routes require explicit classification and focused tests. These guarantees are scoped to the current supported application paths and tested local configuration.

## 15. Guarantees explicitly not claimed

This freeze is not public registration, email verification, password reset, OAuth/MFA, brute-force or rate-limit protection, distributed sessions, production TLS/proxy configuration, a full security audit/penetration test or production deployment security. It does not prove all concurrency histories or a distributed/filesystem transaction guarantee. Old external files and direct administrative database/filesystem writes are outside the supported write-path checks. Session principals remain snapshots; no account editing/revocation workflow was added.

## 16. Deferred work and reproducible freeze checks

Registration, staff attribution, account management, deployment and all later product milestones remain deferred for review. No new migration, speculative constraint, pagination, notification, Redis/Spring Session, JWT/refresh token, AI/ML/IoT or lifecycle/state redesign was introduced.

Run from `backend` with JDK 21/Maven, in sequence:

```powershell
mvn clean test
$env:PAWTRACK_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5432/pawtrack'
$env:PAWTRACK_TEST_DB_USER = 'pawtrack'
$env:PAWTRACK_TEST_DB_PASSWORD = 'pawtrack'
mvn "-Dtest=PostgresMigrationIT,CareRecordPostgresMigrationIT,AlertResolutionPostgresMigrationIT,CatStatusPostgresMigrationIT,RemoveLegacyCatStatusPostgresMigrationIT,UserAccountPostgresMigrationIT,AdoptionOwnershipPostgresMigrationIT" test
mvn "-Dtest=CatLockPostgresRuntimeIT" test
```

The PostgreSQL credentials above are the existing local development fixtures; tests create their own schemas. From `frontend`, with dependencies and Playwright Chromium installed, run serially:

```powershell
npm run build
npm run lint
npm run test:care
npm run test:live
```

The live runner requires free ports 5173/19091; mocked browser tests use 5175. Run browser suites and lint serially: the browser runner clears the shared test-results directory, which can race lint enumeration or remove another suite's evidence. Running live last preserves its screenshots. All final gates passed after resolving an observed lint/output-directory race; live was run last to preserve its evidence.

### P4.4 changed-file inventory

Production: `backend/src/main/java/com/pawtrack/backend/identity/security/SessionSecurityConfiguration.java`, `identity/config/DemoAccounts.java`, `cat/service/CatService.java`, **new** `cat/service/CatImageStorage.java`, `config/WebConfig.java`, `common/api/GlobalExceptionHandler.java`, and `backend/src/main/resources/application.yml` (Java paths after the first share the same backend package root).

Tests under `backend/src/test/java/com/pawtrack/backend/`: **new** `identity/api/ApiDefaultDenyIntegrationTest.java`, **new** `identity/api/DemoAccountSafetyTest.java`, **new** `cat/api/CatImageSecurityIntegrationTest.java`, **new** `support/TestImages.java`; updated `identity/api/StaffAuthorizationIntegrationTest.java`, `cat/api/CatImageUploadIntegrationTest.java`, `cat/api/CatDashboardIntegrationTest.java`, `cat/api/TypedCatStatusIntegrationTest.java`, `adoption/api/AdoptionReviewIntegrationTest.java`, `care/api/AlertResolutionIntegrationTest.java`, `consistency/CatLockPostgresRuntimeIT.java`.

Frontend test: `frontend/tests/care/adopter-ownership.spec.ts`. Documentation: `README.md`, `backend/ARCHITECTURE.md`, **new** `docs/P4_4_ROUTE_INVENTORY.md` and this report. Earlier uncommitted phase files may also appear in git status; they are not additional P4.4 work.
