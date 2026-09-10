# P2.4a — Correctness fixes

Completed 2026-09-10. Scope: the four reviewed correctness fixes and focused regression coverage. P1/P2 workflows, Cat status model, alert lifecycle, care taxonomy, timeline DTO and the inherited `> 39.5` fever rule are preserved. No Flyway migrations were changed or added.

## Files changed

Backend implementation:

- `backend/src/main/java/com/pawtrack/backend/healthdata/repo/HealthDataRepository.java`
- `backend/src/main/java/com/pawtrack/backend/healthdata/service/HealthMonitorService.java`
- `backend/src/main/java/com/pawtrack/backend/healthdata/service/HealthDataService.java`
- `backend/src/main/java/com/pawtrack/backend/cat/service/CatService.java`
- `backend/src/main/java/com/pawtrack/backend/care/service/HealthTimelineService.java`
- `backend/src/main/java/com/pawtrack/backend/healthdata/api/dto/CreateHealthDataRequest.java`
- `backend/src/main/java/com/pawtrack/backend/healthdata/api/HealthDataController.java`

Backend tests:

- `backend/src/test/java/com/pawtrack/backend/healthdata/api/HealthObservationCorrectnessIntegrationTest.java` (new)
- `backend/src/test/java/com/pawtrack/backend/healthdata/repo/HealthDataRepositoryTest.java` (query rename)
- `backend/src/test/java/com/pawtrack/backend/cat/api/CatDashboardIntegrationTest.java` (mock query rename)

Frontend implementation and regression runner:

- `frontend/src/pages/CarePage.tsx`
- `frontend/tests/care/care-workspace.spec.ts` (new)
- `frontend/playwright.config.ts` (new)
- `frontend/package.json`
- `frontend/package-lock.json`
- `.gitignore` (generated Playwright results only)
- `docs/P2_4A_CORRECTNESS_FIXES.md` (this report)

## Latest observation ordering

Every HealthData latest/history query now uses **`ts DESC, id DESC`**. When timestamps tie, the greater observation ID wins.

`findFirstByCatIdOrderByTsDescIdDesc` is used by HealthMonitorService and CatService's dashboard. `findByCatIdOrderByTsDescIdDesc` is used by HealthDataService's health history and HealthTimelineService's observation input. The existing timeline comparator and other entities' ordering were not changed.

Two new non-transactional MockMvc tests submit equal-timestamp observations through real service transactions and reload persisted state:

- **38.00 → 40.00:** later ID is latest; OPEN FEVER exists; Cat is UNDER_OBSERVATION; adoption submission returns 409 without an application; dashboard, health history and timeline agree.
- **40.00 → 38.00:** later normal observation is latest, but the original OPEN alert and blocking status remain, with no resolution timestamp or care record. Explicit resolution then closes that same alert, creates one care record, restores NORMAL and permits adoption submission (201).

Both passed.

## Temperature precision contract

V1 and the entity already use `NUMERIC(5,2)`. The request field now has `@Digits(integer = 3, fraction = 2)`, activated by controller `@Valid`.

Temperature remains optional. Present values must have at most three integer digits and two fractional digits, within -999.99 through 999.99. Unsupported precision/capacity returns the existing HTTP 400 validation response before service writes. No rounding or new medical range validation was added. A supplied `39.500` is rejected because it contains three fractional digits.

Eight parameterized HTTP/persistence cases passed:

| Input | Result |
| --- | --- |
| 39.50 | 200; reloaded as exactly 39.50; no alert; NORMAL |
| 39.51 | 200; reloaded as exactly 39.51; OPEN alert; UNDER_OBSERVATION |
| 999.99 | 200; schema upper boundary preserved; existing fever behavior |
| -999.99 | 200; schema lower boundary preserved; no new medical restriction |
| 39.501, 39.500 | 400; zero observations/alerts; Cat status and updatedAt unchanged |
| 1000.00, -1000.00 | 400; zero observations/alerts; Cat status and updatedAt unchanged |

Two additional HTTP tests accept omitted and explicitly null temperature, persisting a null value without a fever alert. Tests have no outer transaction: assertions read the database after the HTTP transaction completes, so they cannot accidentally inspect an unrounded managed entity.

## Stable selection and context

CarePage commits the first alert to local selection state when a successful queue first contains data and selection is null. The guarded state update applies only to this initial choice; selection no longer follows `queue.data[0]` on subsequent refreshes. Explicit selection still changes context and resets the draft.

Queue insertion, reordering, removal and an empty queue preserve the selected alert. If an external resolution closes it, its unsent note and chosen care type remain visible in a read-only draft field. A locally successful resolution continues to retain the updated timeline after leaving the OPEN queue.

## Timeline readiness guard

Care type, note and submit controls are disabled unless `timeline.isSuccess`. The submit handler checks the same condition, in addition to existing note, pending and closed guards. An initial load failure keeps resolution disabled until successful retry. The UI explains that history must load before resolution. No custom cancellation or request-generation framework was introduced.

The tests also check that a PATCH network failure preserves note/type and permits retry; a subsequent 409 refreshes the queue/timeline, retains the unsubmitted note and never claims success.

## Reproducible browser regressions

From `frontend`, with a supported Node.js installation:

```powershell
npm ci
npx playwright install chromium
npm run test:care
```

The runner starts its own Vite server on **127.0.0.1:5175**, uses project-local pinned `@playwright/test` 1.62.1, and intercepts all `/api/` requests with explicit fixtures. It requires no backend or personal dependency path and does not mutate demo data. The port must be free. Browser binaries must be installed once; Linux hosts may need Playwright's documented system dependencies.

Five scenarios ran at both **1440×1100** and **390×844** in Chromium:

1. Automatic selection and note survive a new queue head and subsequent reordering; explicit selection still works.
2. Externally resolved/removed alert retains context and unsent note, including when the queue becomes empty.
3. Held initial timeline prevents resolution; successful load enables it; pending PATCH prevents duplicates; success performs a fresh timeline GET and shows CLOSED plus the new CareRecord.
4. Failed timeline keeps controls disabled until successful retry.
5. PATCH network failure preserves note/type; retry returning 409 refreshes state without a success message.

All scenarios also check affected context visibility and horizontal overflow at both widths. These are browser behavior tests with mocked APIs, not live-backend end-to-end or visual snapshot tests. The initial runner attempt intercepted Vite's `/src/api/` modules; restricting interception to URL paths starting with `/api/` fixed that test harness issue. The final run passed **10/10** with retries disabled.

## Quality gate

| Check | Final result |
| --- | --- |
| New backend correctness cases | 12 passed |
| Complete backend `clean test` | **63 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| Existing backend cases | All original 51 passed, including concurrency, rollback, adoption, timeline, alert resolution and demo/bootstrap |
| `npm run build` | Passed |
| `npm run lint` | Passed |
| `npm run test:care` | **10 passed**, desktop and narrow Chromium |

The backend suite was run using Java 21 and the existing Maven dependency cache. The portable equivalent is:

```powershell
mvn clean test
```

With a normally configured Maven installation, use `mvn clean test`. Local logs are `backend/p2-4a-test.log` and `frontend/p2-4a-browser.log` (ignored).

## Assumptions and remaining boundaries

- Validation intentionally enforces the existing storage/API contract, not medical plausibility. Internal Java callers are still responsible for supplying valid domain values.
- Timeline success with cached data retains normal React Query background-refresh behavior. This fix does not introduce observation-version checks or stronger freshness semantics.
- At P2.4a, the timeline still displayed one decimal place. P2.4b subsequently aligned both temperature displays to two places; see the freeze report for current verification.
- No PostgreSQL runtime/concurrency tests were added or run. Existing H2 and migration-test evidence remains distinct.
- No P2.4b work, status refactor, authentication, deployment or unrelated cleanup was started.
