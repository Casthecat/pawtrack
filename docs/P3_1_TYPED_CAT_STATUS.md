# P3.1 — Typed Cat Status Backend Migration

Completed and verified on 2026-09-10 on `feat/p3-status-model`. P1/P2 remains frozen at `v1.0-portfolio-core`. This change stops at P3.1: no frontend migration or legacy-column removal.

## 1. Previous production usage inventory

The [complete inventory](P3_1_STATUS_INVENTORY.md) was recorded before implementation. Direct domain consumers were Cat (default, accessor/mutator, eligibility), CatService (creation/manual update), AdoptionService (eligibility/approval), HealthMonitorService (fever), AlertResolutionService (recovery), and CatMapper (both Cat DTOs). CatController and UpdateCatStatusRequest carried legacy PATCH input; CatResponse/CatDetailResponse carried output.

DemoData relied on Cat defaults and health monitoring. CatRepository locks did not contain status predicates; HealthDataService delegated monitoring; timeline assembly did not read Cat status. Other status accessors belong to Alerts, adoption applications and DTOs. V1 created the legacy column; V2–V6 did not change it. Frontend consumers and all directly dependent tests are listed in the inventory.

A final production-wide search confirms Cat has no old status field/getter/setter. Remaining Cat strings are confined to request translation and DTO projection. Alert and application status handling remains separate.

## 2. Files changed

Production Java paths below are relative to `backend/src/main/java/com/pawtrack/backend/`:

- Added `cat/domain/CatHealthStatus.java` and `cat/domain/CatAdoptionStatus.java`.
- Updated `cat/domain/Cat.java`, `cat/api/mapper/CatMapper.java`, `cat/service/CatService.java`.
- Updated `adoption/service/AdoptionService.java`, `healthdata/service/HealthMonitorService.java`, `care/service/AlertResolutionService.java`.

Added schema file: `backend/src/main/resources/db/migration/V7__split_cat_status.sql`.

Test paths below are relative to `backend/src/test/java/com/pawtrack/backend/`:

- Added `cat/api/TypedCatStatusIntegrationTest.java`, `cat/api/CatStatusPostgresMigrationIT.java`, `cat/api/HistoricalMigrationIntegrityTest.java`.
- Updated `adoption/api/AdoptionReviewIntegrationTest.java`, `adoption/service/AdoptionFlowIntegrationTest.java`.
- Updated `care/api/AlertResolutionIntegrationTest.java`, `care/service/DemoCareTimelineIntegrationTest.java`.
- Updated `cat/api/CatControllerWebMvcTest.java`, `cat/api/CatDashboardIntegrationTest.java`, `cat/api/CatImageUploadIntegrationTest.java`.
- Updated `healthdata/api/HealthAlertIntegrationTest.java`, `healthdata/api/HealthObservationCorrectnessIntegrationTest.java`, `healthdata/service/HealthMonitorServiceIntegrationTest.java`.
- Updated `consistency/CatLockPostgresRuntimeIT.java`.

Documentation: updated `backend/ARCHITECTURE.md`; added `docs/P3_1_STATUS_INVENTORY.md` and this report. Total: 26 files. No frontend, README, DTO class, controller, dependency, or V1–V6 edits.

## 3. V7 schema and backfill

V7 adds `cats.health_status varchar(30)` and `cats.adoption_status varchar(30)`, backfills, then sets both NOT NULL with defaults NORMAL and AVAILABLE. Enums use string persistence. The old `cats.status` column and its original default remain physically present.

Exact backfill:

- NORMAL → health NORMAL, adoption AVAILABLE.
- ADOPTABLE → health NORMAL, adoption AVAILABLE.
- UNDER_OBSERVATION → health UNDER_OBSERVATION, adoption AVAILABLE.
- SICK → health SICK, adoption AVAILABLE.
- ADOPTED → health NORMAL, adoption ADOPTED.

ADOPTED → health NORMAL is an explicit historical compatibility assumption. The old field overwrote any independent health dimension, so previous health cannot be recovered from it.

A precondition raises an exception for any unknown/null legacy value before adding columns. No trimming, case folding or fallback to NORMAL occurs. PostgreSQL tests demonstrate failed migration leaves the fixture and schema unchanged and permits retry after an explicit fixture repair. Production V1 already prohibits NULL; unknown strings are technically possible because it has no value CHECK constraint.

## 4. Enum and domain model

`CatHealthStatus` contains only NORMAL, UNDER_OBSERVATION, SICK. `CatAdoptionStatus` contains only AVAILABLE, ADOPTED. Both constructors and persisted new Cats default to NORMAL + AVAILABLE. All six combinations are representable. Null setters fail immediately.

`AdoptionStatus` still describes the separate application lifecycle PENDING/APPROVED/REJECTED. Cat no longer maps the historical status column at all, preventing accidental business reads or dual writes.

## 5. Exact API compatibility

`CatMapper` derives `status` for both existing DTOs: if adoptionStatus is ADOPTED, return `ADOPTED`; otherwise return the health enum name (`SICK`, `UNDER_OBSERVATION`, or `NORMAL`). No typed fields or persistence details are added to public responses.

This preserves the existing list/detail/dashboard/create/upload/PATCH DTO contract. It is deliberately lossy: an adopted cat's independent health status is not visible in this single field. Business logic never consumes this projection. P3.2 will migrate frontend/API consumers and remove the compatibility field and old database column after review.

## 6. Adoption behavior

Eligibility requires AVAILABLE + NORMAL and no OPEN alert, both at submission and approval. Violations retain HTTP 409. Approval changes only adoptionStatus to ADOPTED; healthStatus is preserved. An unhealthy pending cat remains ineligible rather than having health reset to allow approval.

The existing service transaction still approves one application and rejects competing PENDING applications atomically. Cat pessimistic locking, lock acquisition before reading mutable state, rejection semantics, validation and not-found responses remain intact.

## 7. Health monitoring

Fever sets healthStatus to UNDER_OBSERVATION unless it is already SICK. Adoption status never changes, so an adopted normal cat becomes ADOPTED + UNDER_OBSERVATION. Existing OPEN fever deduplication and creation after a previously resolved alert remain intact. Latest-observation ordering, temperature precision and the inherited > 39.5 °C threshold are unchanged.

## 8. Alert resolution

After closing an OPEN alert and saving the linked CareRecord, zero remaining OPEN alerts plus health UNDER_OBSERVATION changes only health to NORMAL. SICK remains SICK; adoption always remains unchanged. This applies equally to available and adopted cats. Cat → Alert/CareRecord lock ordering, flush, transaction rollback, duplicate-resolution 409 and timestamp behavior remain intact.

## 9. Manual PATCH decision

The existing `PATCH /api/cats/{id}/status` body is unchanged. NORMAL/ADOPTABLE translate to health NORMAL; UNDER_OBSERVATION/SICK translate to their health enum. Adoption is never written. ADOPTABLE is accepted as an input alias but now returns NORMAL, as required by the compatibility projection.

To minimize contract changes, supported requests targeting an already adopted cat still return 409 and preserve both fields. ADOPTED, MANUAL_HOLD, lowercase and null input still return HTTP 400 through request validation. The existing direct-service ADOPTED guard also remains. Manual NORMAL does not close an OPEN alert or bypass adoption eligibility.

## 10. Tests added and updated

`TypedCatStatusIntegrationTest` adds 27 executed cases: constructor/HTTP defaults; six persisted combinations with list/detail/dashboard compatibility and submission eligibility; approval preserving health and rejecting competition; two unhealthy-approval conflicts; four fever combinations including adopted/SICK and deduplication; supported/invalid/adopted manual PATCH cases; and OPEN-alert blocking after manual NORMAL. Requests run without a surrounding test transaction and with open-in-view disabled.

`HistoricalMigrationIntegrityTest` adds one test checking V1–V6 against frozen SHA-256 contents, normalizing only platform CRLF conversion. Git comparison against `v1.0-portfolio-core` also finds no V1–V6 or frontend changes.

`CatStatusPostgresMigrationIT` adds four cases: all five known mappings plus real JPA validation/service behavior; and rejection/retry for MANUAL_HOLD, lowercase `normal`, and empty status. The successful case deliberately contradicts the archived column after migration, then loads DTOs and submits/approves through real services, proving the old column is ignored and not synchronized.

Eleven existing test classes now use typed Cat assertions/fixtures. Resolution's five legacy-string cases become six independent-state cases. MANUAL_HOLD was a test-only arbitrary string, not a supported product state; its coverage moves to explicit migration rejection. Existing application/alert status and HTTP contract assertions remain. Demo bootstrap/timeline, precision, rollback, and concurrent behavior tests continue running.

## 11. Complete backend result

Java 21, Maven offline with the existing local dependency cache, 2026-09-10:

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' clean test
```

**92 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** This is the complete default suite, not just focused new tests. Count: frozen 63 + one additional resolution combination + 27 typed cases + one migration-integrity test. Log: `backend/p3-1-test.log` (local ignored output).

## 12. PostgreSQL migration verification

With `PAWTRACK_TEST_JDBC_URL`, `PAWTRACK_TEST_DB_USER` and `PAWTRACK_TEST_DB_PASSWORD` set for the local PostgreSQL 16.11 test connection:

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' '-Dtest=PostgresMigrationIT,CareRecordPostgresMigrationIT,AlertResolutionPostgresMigrationIT,CatStatusPostgresMigrationIT' test
```

**7 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** The three earlier migration checks are retained; four new V7 cases verify V6 data upgrade, non-null varchar columns, retained legacy values, explicit failures/retries, no-op rerun, and actual Spring startup with Hibernate validate/open-in-view=false. Log: `backend/p3-1-migrations.log`.

All migration writes use disposable UUID schemas with cleanup in finally. The real local `public` schema was only inspected before implementation, not upgraded by this task.

## 13. PostgreSQL runtime result

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' '-Dtest=CatLockPostgresRuntimeIT' test
```

**3 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** Flyway now initializes through V7 with Hibernate validate. Competing approvals, competing resolutions, and resolution followed by waiting health input retain their expected outcomes, with both typed dimensions asserted. Existing database-observed lock waits using `pg_blocking_pids` remain unchanged. Log: `backend/p3-1-runtime.log`.

## 14. Assumptions and remaining dependencies

- Read-only local legacy data inspection found NORMAL (2) and UNDER_OBSERVATION (17). This does not establish values in any other database; V7 validates each database itself.
- The historical column is archival, not a current compatibility view. New inserts retain its old database default; subsequent typed writes leave it untouched. Old binaries or scripts writing only that column are not supported concurrently. No dual-write mechanism is introduced.
- H2 create-drop omits the unmapped legacy column. PostgreSQL migration/runtime verification covers the real additive schema separately.
- PostgreSQL varchar columns enforce non-null, but this phase adds no value CHECK constraints. Direct SQL can bypass application rules and enum validity; this remains outside the existing application-lock guarantee.
- Frontend CatStatus types, filters and badges, legacy DTO `status`, and the legacy PATCH body remain transitional dependencies. Adopted health combinations become fully visible only after P3.2 consumers migrate. Frontend/browser suites were not rerun this backend-only phase; HTTP contract compatibility was tested and frontend files are unchanged.
- No actual development database upgrade, deployment or running-demo restart was performed. No new product features, dependencies, public status endpoint, pagination, auth, AI or lifecycle redesign was added. The branch remains uncommitted for review; P3.2 has not begun.
