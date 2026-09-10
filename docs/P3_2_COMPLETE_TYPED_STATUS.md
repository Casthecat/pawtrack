# P3.2 — Complete Typed Status Migration

Completed and verified on 2026-09-10 on `feat/p3-status-model`. P1/P2 remains frozen at `v1.0-portfolio-core`; [P3.1](P3_1_TYPED_CAT_STATUS.md) remains an unchanged historical record of the transitional implementation. Work stops at P3.2.

## 1. Legacy usages discovered

The [pre-implementation inventory](P3_2_STATUS_INVENTORY.md) records all consumers and decisions. Backend compatibility lived in CatResponse/CatDetailResponse, CatMapper, CatController's status PATCH, UpdateCatStatusRequest and CatService.updateStatus. Domain rules already used typed fields exclusively. Demo initialization had no legacy writes.

Frontend consumers were `api/types.ts`, GalleryPage, CatCard, CatDetailPage and the shared StatusBadge. Only the dashboard mock and live dashboard assertion depended on legacy Cat status in browser tests. No frontend API function or staff page called the manual status PATCH. Application, Alert, HTTP and CSS status usages were identified separately and preserved.

## 2. Files changed

27 files including additions/deletion. Paths are relative to the repository root.

Backend production:

- Updated `backend/src/main/java/com/pawtrack/backend/cat/api/CatController.java`.
- Updated `backend/src/main/java/com/pawtrack/backend/cat/api/dto/CatResponse.java` and `CatDetailResponse.java`.
- Deleted `backend/src/main/java/com/pawtrack/backend/cat/api/dto/UpdateCatStatusRequest.java`.
- Updated `backend/src/main/java/com/pawtrack/backend/cat/api/mapper/CatMapper.java`.
- Updated `backend/src/main/java/com/pawtrack/backend/cat/service/CatService.java`.
- Added `backend/src/main/resources/db/migration/V8__remove_legacy_cat_status.sql`.

Backend test paths under `backend/src/test/java/com/pawtrack/backend/`:

- Updated `adoption/api/AdoptionReviewIntegrationTest.java`.
- Updated `cat/api/CatControllerWebMvcTest.java`, `cat/api/CatImageUploadIntegrationTest.java`, `cat/api/TypedCatStatusIntegrationTest.java`.
- Updated `cat/api/CatStatusPostgresMigrationIT.java`, `cat/api/HistoricalMigrationIntegrityTest.java`.
- Added `cat/api/RemoveLegacyCatStatusPostgresMigrationIT.java`.

Frontend:

- Updated `frontend/src/api/types.ts`.
- Added `frontend/src/components/CatStatusBadges.tsx`; updated `CatCard.tsx` and `StatusBadge.tsx` in that directory.
- Updated `frontend/src/pages/GalleryPage.tsx`, `frontend/src/pages/CatDetailPage.tsx`, `frontend/src/styles/globals.css`.
- Updated `frontend/tests/care/care-workspace.spec.ts` and `frontend/tests/live/milestone.spec.ts`.
- Added `frontend/tests/care/typed-cat-status.spec.ts`.

Documentation: updated `backend/ARCHITECTURE.md`; added `docs/P3_2_STATUS_INVENTORY.md` and this report. README, P3.1 documents, V1–V7, demo seeds, dependencies and Playwright runner configuration remain unchanged.

## 3. Final public Cat contract

CatResponse contains `id`, `name`, `healthStatus`, `adoptionStatus`, `streamUrl`, `imageUrl`, `createdAt`, `updatedAt`. CatDetailResponse contains those fields plus `temperatureC` and `hasActiveAlert`.

- `healthStatus`: NORMAL | UNDER_OBSERVATION | SICK.
- `adoptionStatus`: AVAILABLE | ADOPTED.

Both Java fields are enums serialized as their names. No Cat `status` field remains. List/single-cat/create/upload responses use CatResponse; dashboard uses CatDetailResponse. No new response endpoint was added. Application and Alert DTO statuses retain their independent lifecycle meanings.

## 4. Frontend typed model

CatHealthStatus and CatAdoptionStatus replace CatStatus. Cat and its CatDetail extension require both properties. No single mutable or derived replacement Cat status exists. CatStatusBadges renders two independent label maps; the existing StatusBadge now serves only adoption applications.

## 5. Gallery and detail semantics

Cards and details show both facts: Awaiting a home / Found a home, plus Health: normal / Under observation / Sick · receiving care. Existing badge colors, typography and card structure remain; a wrapping flex row accommodates both labels on narrow screens.

Ready to meet count/filter requires AVAILABLE + NORMAL. Found a home requires ADOPTED, regardless of health. Thus available-but-sick cats do not appear ready, and adopted-but-sick cats stay in the found-home filter. The gallery contract lacks active-alert information; no client-only alert fact was invented or new category added.

Details independently show health for adopted cats. An adopted cat with an alert gets a care-review notice that explicitly retains its found-home status, not a message suggesting adoption has been reversed. Existing pause wording remains for available cats with an alert.

## 6. Eligibility

Detail presentation requires AVAILABLE + NORMAL + `!hasActiveAlert` for the application form. Backend submission and approval retain the same typed-state plus OPEN-alert validation and HTTP 409 behavior. Approval changes only adoption state; health monitoring and final-alert resolution change only health. SICK remains independent. No third eligibility status or new business transition was introduced.

## 7. Manual endpoint decision

Removed `PATCH /api/cats/{id}/status`, its request class and service method. It had no actual frontend caller and was unnecessary for either existing demo slice. No replacement health-status endpoint was added, as permitted by the task. The removed route returns 404 and does not mutate either dimension across all six combinations. Adoption remains managed through application approval; existing observation/resolution APIs handle the demonstrated care flow.

## 8. V8 design

V7 remains responsible for deterministic legacy backfill, including the documented historical ADOPTED → health NORMAL assumption. V8 first verifies that both typed columns have NOT NULL constraints and that no typed value is null, then drops only `cats.status`. It neither reads nor re-backfills from the archived value. Final Cat state columns are `health_status` and `adoption_status`.

The V8 test deliberately sets archived status to an unsupported stale string after V7, while preserving all five migrated cases and adding two adopted/non-normal typed combinations. V8 succeeds, preserves an exact before/after snapshot of typed rows, removes the old column and passes real Hibernate validation. Separate cases verify nullable typed schema drift fails without dropping the archive.

## 9. Proof of compatibility removal

Production searches for `CatStatus` (exact type), `cat.status`, `c.status`, ADOPTABLE, compatibilityStatus, UpdateCatStatusRequest and updateStatus return no matches in Java/frontend production source. Remaining getStatus/setStatus methods concern Alerts or applications; remaining frontend `.status` usages concern applications, HTTP errors and CSS selectors. No component collapses two Cat dimensions into a single status.

HTTP tests verify both explicit fields and absence of legacy output across list/detail/dashboard/create/upload paths. PostgreSQL verifies column absence. Git comparison against the starting P3.1 HEAD finds no changes to V1–V7 or P3.1 documents; the content-integrity test also protects V1–V7.

## 10. Tests added and updated

- TypedCatStatusIntegrationTest checks six persisted combinations in all read DTOs, defaults, approval/competition, unhealthy approval rejection, fever/deduplication, OPEN-alert blocking despite normal health, and removal of the generic PATCH.
- Existing Cat MVC/upload tests now assert typed output and absent legacy status. AdoptionReviewIntegrationTest expects the removed route's 404 while preserving its adopted-state checks. Existing resolution matrices, rollback and concurrency coverage are retained unchanged.
- HistoricalMigrationIntegrityTest adds the completed P3.1 V7 content hash. The historical V7 PostgreSQL test keeps all migration/data checks and its V7 target; only current DTO assertions are adapted and its application startup explicitly pinned to V7.
- New RemoveLegacyCatStatusPostgresMigrationIT adds three cases: upgrade/preservation/JPA validation and two invalid nullable-column preconditions. Earlier migration tests retain their older version targets.
- New mocked browser tests verify filters/counts, both health/adoption labels, all six detail combinations, active-alert gating and adopted care wording on desktop and 390px screens. Existing six care tests, including selection/draft/readiness/network/conflict behavior, retain their assertions; only the dashboard fixture changes.
- The existing live test retains both original vertical slices and adds six-cat/Nori startup checks plus a real adopted-fever event and visible independent labels on detail and gallery.

## 11. Complete backend result

Java 21, using the existing Maven cache:

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' clean test
```

**86 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** The count changes from P3.1's 92 because twelve obsolete compatibility-PATCH input cases were replaced by six removed-route combination cases. Other domain, demo and concurrency coverage remains. Local ignored log: `backend/p3-2-test.log`.

## 12. Frontend gates

- `npm run build`: passed, TypeScript and Vite production bundle succeeded.
- `npm run lint`: passed, including the final browser-test edits.
- `npm run test:care`: **16 passed**, desktop and narrow projects, zero retries.
- `npm run test:live`: **1 passed**, zero retries, detailed below.

Local ignored logs: `frontend/p3-2-build.log`, `p3-2-lint.log`, `p3-2-care.log`, `p3-2-live.log`. An initial sandboxed esbuild attempt hit filesystem access restrictions; rerunning with normal authorized tool access passed without a code workaround.

## 13. PostgreSQL migration result

On local PostgreSQL 16.11, with PAWTRACK_TEST_JDBC_URL/DB_USER/DB_PASSWORD configured:

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' '-Dtest=PostgresMigrationIT,CareRecordPostgresMigrationIT,AlertResolutionPostgresMigrationIT,CatStatusPostgresMigrationIT,RemoveLegacyCatStatusPostgresMigrationIT' test
```

**10 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** All writes use disposable UUID schemas cleaned in finally. V8's successful case also inserts a new Cat after validating the migrated schema. Negative-case migration errors in the log are expected assertions with successful rollback. Local log: `backend/p3-2-migrations.log`.

## 14. PostgreSQL runtime result

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' '-Dtest=CatLockPostgresRuntimeIT' test
```

**3 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** The existing runner now automatically migrates through V8 and validates Hibernate before competing approvals, competing resolutions, and a waiting health write after resolution. Cat locks and database-observed waits are unchanged. Local log: `backend/p3-2-runtime.log`.

## 15. Live demo result

`npm run test:live` passed against its own fresh H2 demo backend on 19091 and Vite on 5173, without API interception or server reuse. Java 21 and MAVEN_OPTS pointing at the existing local Maven cache were supplied to the runner. The existing application processes and development PostgreSQL schema were not upgraded by this task.

Verified six cats; Nori starts AVAILABLE + UNDER_OBSERVATION with an OPEN fever alert and no application form. Mochi accepts two applications; approval selects one and declines the competing application. Nori's care resolution closes the alert, records the note, restores NORMAL and reopens the application form. Finally a controlled observation POST makes already-adopted Mochi UNDER_OBSERVATION while retaining ADOPTED. Both badges remain visible in details and Found a home gallery; applications remain unavailable. No page errors or horizontal overflow were observed.

Screenshots under `frontend/test-results/live/milestone-real-demo-comple-f8d87-on-and-care-vertical-slices/` include `adopted-under-observation-narrow.png` and `adopted-under-observation-gallery.png`; both were visually inspected. Care open/closed screenshots are also retained in that ignored artifact directory.

## 16. Deferred issues and rollout boundary

- Gallery Ready to meet is limited by its existing list contract: it cannot detect a normal-health cat with an OPEN alert. Details and backend checks remain authoritative; the two badges avoid claiming confirmed application eligibility.
- No manual health editor is exposed. SICK and adopted/non-normal combinations are covered by domain/API fixtures, while the live independent combination uses the existing observation flow.
- This intentionally breaks the old Cat API contract and removes the database column on V8 migration. Frontend and backend must be updated together; this phase adds no compatibility layer or deployment infrastructure. The real development `public` schema was not migrated.
- Varchar enum validity and cross-writer invariants are still application concerns; no extra database value constraints, pagination, stale-decision protection, auth, staff identity, AI, IoT, notifications or new milestone work was added.
- Changes remain uncommitted for review. P3.1 history and the frozen P1/P2 tag remain intact. Stop after P3.2.
