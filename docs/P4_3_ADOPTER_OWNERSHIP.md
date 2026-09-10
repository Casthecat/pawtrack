# P4.3 Adopter Application Ownership — quality report

Verified 2026-09-10. Scope ends at P4.3. The existing P4.2 working-tree changes were preserved; this report describes the incremental P4.3 work. No commit, deployment or normal development-schema upgrade was performed.

## 1. Pre-change adoption ownership inventory

The [pre-change inventory](P4_3_ADOPTION_INVENTORY.md) was recorded before changing implementation. It covers the entity, V2/V4/V9 schema, DTOs/mapper, repository/service/controller, security, all name/email uses, frontend submission/receipt/review paths and test fixtures.

Before P4.3, the request's name/email were authoritative, duplicates were cat + case-insensitive email + PENDING, and the receipt was public by ID. No account relation or owner-list API existed. The receipt was read through the same GET endpoint after submission, via staff review links and via saved/direct URLs. Review and receipt DTOs both contained applicant identity. Email equality was not ownership evidence.

## 2. Files changed in P4.3

Paths below are relative to the repository; **new** means introduced in P4.3, not an earlier uncommitted phase.

Backend production, under `backend/src/main/java/com/pawtrack/backend/`:

- `adoption/api/AdoptionController.java` — explicit principal for submission/receipt.
- **new** `adoption/api/MyAdoptionsController.java` — owner-list endpoint.
- `adoption/api/dto/AdoptionApplicationRequest.java` — remove identity input fields.
- `adoption/domain/AdoptionApplication.java` — optional account relation.
- `adoption/repo/AdoptionApplicationRepository.java` — owner lookup and account duplicate predicate.
- `adoption/service/AdoptionService.java` — owner-bound creation, object authorization and DTO list assembly.
- `identity/security/SessionSecurityConfiguration.java` — ADOPTER submission/list, authenticated receipt and role-specific denial message.
- `identity/config/DemoAccounts.java` — second demo-only ADOPTER fixture.
- **new** `backend/src/main/resources/db/migration/V10__adoption_account_owner.sql`.

Backend tests, under `backend/src/test/java/com/pawtrack/backend/`:

- **new** `adoption/api/AdoptionOwnershipIntegrationTest.java` and `AdoptionOwnershipPostgresMigrationIT.java`.
- **new** `support/TestAccounts.java` — stored accounts and explicit principals for business fixtures.
- `adoption/api/AdoptionReviewIntegrationTest.java` — owner-bound submission fixtures and authenticated receipt reads; existing decision/concurrency assertions retained.
- `care/api/AlertResolutionIntegrationTest.java`, `care/service/DemoCareTimelineIntegrationTest.java`, `cat/api/TypedCatStatusIntegrationTest.java`, `healthdata/api/HealthObservationCorrectnessIntegrationTest.java` — authenticated adopter submission fixtures.
- `consistency/CatLockPostgresRuntimeIT.java` — stored account/principal submission fixtures; lock coordination and outcome assertions unchanged.
- `identity/api/StaffAuthorizationIntegrationTest.java` — retains all staff role tests and updates the deliberately public P4.2 submission/receipt expectations to 401.
- `identity/api/DemoAccountsIntegrationTest.java` — verify distinct demo adopters and encoded passwords.
- `cat/api/CatStatusPostgresMigrationIT.java`, `identity/api/UserAccountPostgresMigrationIT.java`, `support/PreIdentityMigrationApplication.java` — retain historical migration checks while validating the current owner-bearing application model only against V10.
- `support/StaffMvcTestConfiguration.java` — use AccountPrincipal in staff business fixtures.

Frontend production, under `frontend/src/`:

- `App.tsx`, `api/pawtrack.ts`, `api/types.ts`, `api/axiosInstance.ts`.
- `components/AccountNavigation.tsx`; **new** `components/RequireAccount.tsx`.
- **new** `hooks/safeReturnPath.ts`.
- `pages/LoginPage.tsx`, `pages/CatDetailPage.tsx`, `pages/ApplicationPage.tsx`; **new** `pages/MyApplicationsPage.tsx`.
- `styles/globals.css` — small identity/list styling using the existing design.

Browser tests and documentation:

- **new** `frontend/tests/care/adopter-ownership.spec.ts`.
- `frontend/tests/care/typed-cat-status.spec.ts`, `frontend/tests/live/milestone.spec.ts`.
- `README.md`, `backend/ARCHITECTURE.md`; **new** `docs/P4_3_ADOPTION_INVENTORY.md` and this report.

ReviewPage, CarePage, health/care production code, Cat locking, UserAccount schema, V1–V9 and prior quality reports were not modified in P4.3. Other P4.2 files may still appear in `git status` because that phase was already uncommitted at the start.

## 3. V10 schema, foreign key and index

V10 adds nullable `adoption_applications.adopter_account_id BIGINT` and `fk_adoption_account` referencing `user_accounts(id) ON DELETE RESTRICT`. Account deletion cannot silently delete or orphan applications. There is no account deletion feature.

`idx_adoptions_owner_created` indexes `(adopter_account_id, created_at DESC, id DESC)` for owner lookup and deterministic newest-first listing. No join table, speculative global unique constraint or snapshot-column change was added. The existing cat/status index remains available for duplicate checks.

The lazy optional ManyToOne relation has no delete cascade. Historical rows retain NULL; new supported service/API submissions always set the owner. Because historical compatibility requires a nullable column, direct SQL/repository fixture insertion can still create unowned rows. The supported creation contract is enforced in the service, not by pretending the schema can distinguish old versus new NULL rows.

## 4. New submission identity contract

POST `/api/adoptions` requires an authenticated ADOPTER and valid CSRF. Request body:

```json
{"catId":4,"notes":"A quiet home."}
```

`catId` is required and positive; `notes` is optional and limited to 2000 characters. Success remains 201 with the existing explicit application DTO.

The controller passes AccountPrincipal to the transactional service. The service verifies ADOPTER, loads UserAccount by the trusted `accountId`, then stores the relation and the account's current displayName and normalized email as submission-time snapshots. Name/email are not in the request DTO. Under the existing Jackson unknown-property behavior, extra old identity fields are ignored, never used to identify or select the owner; the spoofing test proves this with another account's email and a forged account ID. No header or request parameter supplies identity.

Anonymous submission returns JSON 401. STAFF submission with valid CSRF returns JSON 403, `Adopter access required.` A signed-in ADOPTER without CSRF receives 403 before business processing. Existing availability/alert validation and 409 behavior are retained.

## 5. Ownership authorization and route classification

The adoption delta from the [P4.2 inventory](P4_2_ROUTE_INVENTORY.md) is explicit:

- POST `/api/adoptions`: ROLE_ADOPTER.
- GET/HEAD `/api/adoptions`: ROLE_STAFF, unchanged review queue.
- PATCH `/api/adoptions/{id}/approve` and `/reject`: ROLE_STAFF, unchanged.
- GET/HEAD `/api/adoptions/{id}`: authenticated at the filter, then service-level object authorization.
- `/api/me/adoptions`: ROLE_ADOPTER; the new GET mapping returns the owner list.

For receipts, STAFF may read any application. ADOPTER must have an account ID equal to `application.adopterAccount.id`. A missing row, a different owner or a NULL owner results in the project's normal EntityNotFoundException/JSON 404, `Adoption application not found: {id}`. No applicant DTO is assembled for a denied caller. Anonymous receives 401. Receipt HEAD follows the same service authorization.

All public cat/detail/dashboard/image reads and existing staff care-note, health-history, alert and management boundaries remain as in P4.2. Auth/session lifecycle rules are unchanged. The existing permit-all fallback for unmatched routes is retained; new routes still need classification. Ownership is enforced on the backend, not inferred from frontend hiding or email text.

## 6. Historical NULL-owner behavior

V10 performs no backfill. Even a historical snapshot email exactly matching a UserAccount does not establish ownership. Such rows remain in staff review and STAFF receipt access, and can still be approved or rejected. They are absent from every adopter's My applications list and inaccessible to ADOPTER receipt requests.

No history is deleted; no application claiming mechanism is added. This is a deliberate migration boundary, not missing data that can safely be repaired using email equality.

## 7. Duplicate-application rule

Supported submissions enforce same Cat + same adopter account + PENDING → 409 under the original pessimistic Cat lock. Snapshot email is not the duplicate key. Tests change the stored snapshot email and still observe the account conflict, allow a different account to apply to the same Cat, and allow reapplication after rejection.

A concurrent same-account test yields exactly one 201 and one 409. No uniqueness spanning terminal decisions was introduced. As before, direct database writes outside the service lock protocol are not covered by this application-level invariant.

## 8. My applications API and UI

GET `/api/me/adoptions` takes its owner ID only from the authenticated principal and returns the caller's rows as the existing AdoptionApplicationResponse array. Ordering is `createdAt DESC, id DESC`; an empty list is `[]`. A read-only service transaction fetches Cat and assembles DTOs with open-in-view disabled. No UserAccount, password hash or account entity is serialized.

The SPA consistently uses `/applications` for My applications. Its small list displays Cat, decision status, submission time and owned receipt links. ADOPTER gets a navigation link; anonymous users must log in; STAFF sees an adopter-account requirement and a staff review link. There is no account profile/dashboard or pagination feature.

## 9. Frontend apply, login and receipt behavior

Eligible Cat pages remain public. Anonymous visitors see Sign in to apply, with no editable applicant identity. Login returns through router state to that Cat. ADOPTER sees the account name/email as non-editable text, can add notes and submits only catId/notes. STAFF sees that an adopter account is required. Existing unavailable-Cat states remain intact.

The return-path helper accepts only `/staff`, `/staff/care`, `/applications`, positive numeric receipt paths and positive numeric Cat paths. External URLs, encoded destinations and query redirects are not accepted. Without an intended destination, STAFF goes to review and ADOPTER to My applications.

ApplicationPage is behind the authenticated route guard. Owner/STAFF receipts load normally; wrong ADOPTER sees Application unavailable with no applicant content. A 401 triggers login rather than masquerading as 404 or an empty list; login can return to the same receipt.

Receipt/list query keys include the current account ID. API 401 clears receipt and owner-list caches as well as the existing staff private caches. Login/logout cache clearing is retained. Tests switch from a previously loaded owner receipt to another account and verify that the earlier applicant data is not displayed. Full-session-expiry draft preservation is not added.

## 10. CSRF behavior

The P4.2 centralized Axios interceptor is retained: initialize GET csrf, attach the returned header/token to unsafe requests, reacquire after login/reload as needed, submit logout with CSRF and clear in-memory state. Adoption uses this same mechanism. No token is stored in localStorage/sessionStorage, no adoption-specific token path exists, and failed mutations are not automatically replayed.

The P4.1 server matcher still checks all unsafe auth requests and all authenticated unsafe requests. Authorization now rejects anonymous adoption POST. Real and mocked browser tests verify submission using the shared mechanism; backend STAFF role-denial tests supply valid CSRF to distinguish authorization from CSRF rejection.

## 11. Tests added and regressions retained

Eight new backend integration tests cover:

- Anonymous/STAFF/missing-CSRF submission denial and authenticated ADOPTER success.
- Stored owner ID, account-derived snapshots and forged-body identity isolation.
- Account duplicate checks despite changed snapshot email, different-account submissions and reapplication after rejection.
- Owner/STAFF receipt access, anonymous 401, non-owner/missing 404 and receipt HEAD.
- Matching-email historical NULL-owner denial, absence from owner lists, staff reading/approval and no interference with a new owned application.
- Deterministic owner list ordering with tied timestamps, isolation from other/legacy rows and OSIV disabled.
- Empty owner list and ADOPTER-only list authorization.
- Concurrent same-account submission serialization.

One new PostgreSQL integration test covers V9→V10 schema/data/FK/index and current Hibernate validation. Five new browser scenarios run at both desktop and narrow sizes, giving ten added executions. Existing business tests use stored account/principal fixtures where submission now requires them; existing staff security tests continue using real login sessions. Approval/concurrency and care regression assertions remain.

## 12. Backend complete-suite result

`mvn clean test`: **127 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**. This includes demo/bootstrap, P4.1 session/CSRF, P4.2 staff boundaries, existing business regressions and the eight new ownership tests. Final complete-suite log: `backend/p4-3-test.log`.

## 13. Frontend build, lint and browser results

- `npm run build`: passed TypeScript and Vite production build.
- `npm run lint`: passed.
- `npm run test:care`: **34 passed**, no retries, including all staff care/session and typed-status regressions plus ten ownership executions.

Local logs: `frontend/p4-3-build.log`, `p4-3-lint.log`, `p4-3-care.log`. The authenticated apply form, narrow owner list and existing care screenshots were inspected. Styling was adjusted to use the existing heading/card design, then browser validation was repeated.

## 14. PostgreSQL migration and runtime results

All migration tests through V10: **12 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**. Executed PostgresMigrationIT, CareRecordPostgresMigrationIT, AlertResolutionPostgresMigrationIT, CatStatusPostgresMigrationIT, RemoveLegacyCatStatusPostgresMigrationIT, UserAccountPostgresMigrationIT and AdoptionOwnershipPostgresMigrationIT.

The new test verifies nullable bigint owner, the owner/date/id index, surviving historical NULL-owner data even with a matching existing account email, persisted owned applications, rejection of nonexistent owners, account deletion rejection and Hibernate `ddl-auto=validate` at V10.

Historical V7/V8 tests retain their SQL and typed-Cat validation against those schemas. Their limited validation configuration excludes the now-V10-dependent adoption entity. CatStatusPostgresMigrationIT still tests adoption/approval after subsequently upgrading the disposable fixture to current V10. V9 identity schema assertions run at V9 before starting the current full entity model at V10. No old SQL migration was rewritten to satisfy a newer mapping.

`CatLockPostgresRuntimeIT`: **3 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**. Its coordinated approval/resolution/health-write lock histories are unchanged; application fixtures now have accounts. All PostgreSQL checks use disposable UUID schemas and clean only those schemas. The normal development schema was not migrated. Logs: `backend/p4-3-migrations.log`, `backend/p4-3-runtime.log`.

## 15. Live real-session ownership demo

`npm run test:live`: **1 passed**, no retries, with no API interception. The runner uses its own disposable backend and real session cookies/CSRF.

The demo starts anonymously at Mochi, redirects to login, submits as the first ADOPTER and verifies its receipt/list. It signs out and submits a competing application as a second ADOPTER, proves the first receipt returns 404 and displays no applicant content, then proves anonymous receipt access is 401. STAFF logs in through the UI, approves an application, verifies both terminal outcomes and completes the existing desktop/narrow Nori care workflow. Finally ADOPTER staff access is 403, public browsing still works and the owner sees its updated approved receipt/list.

The second ADOPTER is a small demo-profile-only fixture, not public registration. README documents the fictional credentials; non-demo account provisioning behavior remains unchanged.

Log: `frontend/p4-3-live.log`. Generated evidence under `frontend/test-results/live/milestone-real-demo-comple-f8d87-on-and-care-vertical-slices/` includes `adopter-apply-desktop.png`, `my-applications-narrow.png`, `care-open-narrow.png`, `care-closed-desktop.png`, `staff-login-narrow.png` and adopted-under-observation screenshots. Generated logs/screenshots are local artifacts, not source changes.

## 16. Privacy guarantees now proven

Knowing an application ID no longer grants anonymous receipt access. An authenticated adopter cannot read another account's receipt or list entries, claim a matching-email historical row or supply request identity to create an application as another account. Owner checks use account IDs, new supported submissions have owners, and historical review remains available to authorized STAFF.

These guarantees cover the current supported API/SPA paths. They do not establish production deployment security, resistance to every possible side channel, all database interleavings or safety of direct SQL outside application constraints. The inherited session principal is still a snapshot; account editing/revocation workflows were not introduced. V10's nullable historical compatibility and the existing explicit route inventory remain maintenance obligations.

## 17. Intentionally deferred identity features

No registration, email verification, password reset, OAuth, MFA, JWT, staff action attribution, account editing/deletion UI, manual claiming, notifications, pagination, lifecycle redesign, deployment infrastructure or P4.4 hardening was implemented. Cat locking and health/care behavior remain unchanged. Work stops after P4.3 for review.
