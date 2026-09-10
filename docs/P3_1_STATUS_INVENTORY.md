# P3.1 pre-implementation Cat status inventory

Recorded before implementation on branch `feat/p3-status-model`, with a clean worktree and tag `v1.0-portfolio-core` present. Scope includes production Java, all tests, migrations, demo initialization and the frontend-facing contract. The following describes the old implementation, not the completed P3.1 model.

## All production Cat status usages

- `cat/domain/Cat.java`: persistent String `status` defaults to NORMAL; public getStatus/setStatus; isAvailableForAdoption accepts NORMAL or ADOPTABLE.
- `cat/service/CatService.java`: create explicitly sets NORMAL; updateStatus rejects an already ADOPTED cat or requested ADOPTED, then directly writes the supplied string. List/get/upload return Cat to the mapper; dashboard also maps Cat status. All existing-cat mutation paths retain Cat locking.
- `adoption/service/AdoptionService.java`: approve writes Cat ADOPTED; requireAvailable calls Cat.isAvailableForAdoption and separately rejects any OPEN alert. Application PENDING/APPROVED/REJECTED transitions are a different domain.
- `healthdata/service/HealthMonitorService.java`: fever avoids overwriting ADOPTED or SICK; otherwise sets UNDER_OBSERVATION. This prevents adopted cats from representing their independent health state.
- `care/service/AlertResolutionService.java`: after closing an alert and recording care, no remaining OPEN plus Cat UNDER_OBSERVATION changes Cat to NORMAL.
- `cat/api/mapper/CatMapper.java`: toResponse and toDetailResponse copy Cat.getStatus into the outgoing DTO.
- `cat/api/CatController.java`: PATCH /api/cats/{id}/status forwards the old request string to CatService. Create, list, single-cat, dashboard, upload and status-update responses expose the old DTO status.
- `cat/api/dto/UpdateCatStatusRequest.java`: non-null String, restricted by Bean Validation to NORMAL, UNDER_OBSERVATION, SICK or ADOPTABLE. ADOPTED input is HTTP 400; a supported input targeting an adopted cat gets service 409.
- `cat/api/dto/CatResponse.java` and `CatDetailResponse.java`: constructor/getter String status, used by the unchanged frontend. These are compatibility outputs, not separate persisted state.

Other relevant production paths inspected:

- `config/DemoData.java`: six cats use Cat constructor defaults; Nori becomes UNDER_OBSERVATION through HealthDataService/HealthMonitorService. No direct legacy status assignment.
- `cat/repo/CatRepository.java`: Cat pessimistic-lock query has no status predicate. No other repository query filters on cats.status.
- `healthdata/service/HealthDataService.java`: delegates monitoring; no independent Cat status rule.
- `care/service/HealthTimelineService.java`: returns cat ID/name plus events; does not expose Cat status. Alert/care mappers do not read Cat status.
- Other getStatus/setStatus occurrences belong to Alert, adoption applications, their DTOs or HTTP response assertions. AlertStatus and AdoptionStatus must retain their existing meanings.

## Schema and existing values

- V1 creates cats.status as varchar(50), NOT NULL, default NORMAL, without an enum/check constraint.
- V2 and V4 contain adoption-application status, not Cat status. V3 adds Cat media fields. V5/V6 add care/resolution fields. None of V2–V6 changes cats.status.
- Supported legacy values across the API/model/tests: NORMAL, ADOPTABLE, UNDER_OBSERVATION, SICK, ADOPTED.
- Read-only inspection of local `public.cats` found NORMAL (2 rows) and UNDER_OBSERVATION (17 rows), with no other values. This is evidence for this local database only, not a reason to accept unknown values elsewhere.
- An existing resolution test writes MANUAL_HOLD directly through the unconstrained entity. It is not an accepted public API value or current product requirement. V7 must reject such unknown historical data rather than inventing a new enum or silently normalizing it.
- V1–V6 SHA-256 values were captured before edits; the frozen Git tag also provides the immutable migration baseline for the final diff check.

## Tests directly using legacy Cat entity status

All paths below are under `backend/src/test/java/com/pawtrack/backend`:

- `adoption/service/AdoptionFlowIntegrationTest.java`: NORMAL fixture; adopted Cat assertions.
- `adoption/api/AdoptionReviewIntegrationTest.java`: normal/adopted assertions, unavailable-state fixtures, concurrent approval, adopted fever and resolution, manual PATCH protection.
- `healthdata/service/HealthMonitorServiceIntegrationTest.java`: observation-state assertion.
- `healthdata/api/HealthAlertIntegrationTest.java`: observation-state assertion.
- `healthdata/api/HealthObservationCorrectnessIntegrationTest.java`: blocking/recovery, threshold and precision no-write state assertions.
- `care/service/DemoCareTimelineIntegrationTest.java`: Nori observation-state assertion.
- `care/api/AlertResolutionIntegrationTest.java`: observation fixture; resolution/no-recovery/rollback/concurrency assertions; arbitrary old-status parameterization including ADOPTABLE and MANUAL_HOLD.
- `cat/api/CatControllerWebMvcTest.java`, `CatDashboardIntegrationTest.java`, `CatImageUploadIntegrationTest.java`: NORMAL fixture setup; existing HTTP status fields remain compatibility assertions.
- `consistency/CatLockPostgresRuntimeIT.java`: Cat state assertions after approval, resolution and health-write competition.

CatHealthTimelineIntegrationTest and AlertQueueIntegrationTest create Cats through defaults but their explicit status assignments concern Alerts. The three existing PostgreSQL migration tests insert legacy-schema Cat fixtures and intentionally target V4/V5/V6; retain those historical upgrade checks.

## Unchanged frontend dependencies

`frontend/src/api/types.ts` retains the CatStatus string union. GalleryPage filters/counts normal/adoptable/adopted cats. CatDetailPage combines the legacy status with hasActiveAlert for application availability. CatCard and StatusBadge render that status. Existing API methods expect these DTOs. CarePage uses Alert state rather than Cat status. ApplicationPage/ReviewPage use adoption-application status. No frontend components or consumers will migrate in P3.1.

## Implementation decisions made from this inventory

- Add only CatHealthStatus {NORMAL, UNDER_OBSERVATION, SICK} and CatAdoptionStatus {AVAILABLE, ADOPTED}.
- V7 adds non-null varchar fields and backfills the five known legacy values. Historical ADOPTED maps to health NORMAL because the overwritten health dimension is unrecoverable. Unknown values fail migration explicitly.
- Remove legacy status from the Cat entity mapping entirely. The database column remains an inert historical compatibility column, with its existing default; no service reads it, synchronizes it or uses it as current state. Old application binaries/direct SQL writers are not supported concurrently with the typed backend.
- Derive outgoing status in CatMapper: ADOPTED first, then SICK, then UNDER_OBSERVATION, otherwise NORMAL. No new typed fields are added to public DTOs in this phase.
- Preserve old manual PATCH protection on already adopted cats. For other cats, NORMAL/ADOPTABLE mean health NORMAL; UNDER_OBSERVATION/SICK affect only health. Adoption status can change through approval, not through ordinary PATCH.
