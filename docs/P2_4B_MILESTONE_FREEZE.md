# P2.4b — Milestone freeze verification

Completed 2026-09-10. P1/P2 is ready for freeze review. No later phase has been started. The changes are limited to temperature presentation, targeted PostgreSQL verification, portfolio-facing cleanup and reproducible live-demo checks.

## Files changed (18)

| File | Change |
| --- | --- |
| `frontend/src/components/HealthTimeline.tsx` | Display measurement values with two decimals |
| `frontend/src/pages/CatDetailPage.tsx` | Same display precision in the public profile |
| `frontend/tests/care/care-workspace.spec.ts` | Desktop/narrow assertions for 38.40, 39.50, 39.51 and 40.00; public-profile 39.51 assertion |
| `frontend/playwright.live.config.ts` | Separate real-backend runner, fresh demo process, one worker, no retries |
| `frontend/tests/live/milestone.spec.ts` | Unmocked adoption/care walkthrough and narrow Care verification |
| `frontend/package.json` | Add `npm run test:live` |
| `frontend/src/pages/HomePage.tsx` | Remove unused, unreferenced starter page containing obsolete setup instructions |
| `backend/src/test/java/com/pawtrack/backend/consistency/CatLockPostgresRuntimeIT.java` | Three opt-in real PostgreSQL Spring/JPA concurrency cases |
| `backend/src/main/java/com/pawtrack/backend/alert/domain/Alert.java` | Remove commented-out updatedAt/migration advice; preserve all executable behavior |
| `backend/src/main/java/com/pawtrack/backend/alert/repo/AlertRepository.java` | Replace conversational comments with brief query purpose descriptions |
| `README.md` | Current milestone, portable commands, verification categories and limits |
| `backend/ARCHITECTURE.md` | Accurate DTO/transaction scope, ordering, precision, frontend guards and test evidence |
| `docs/PROJECT_REVIEW.md` | Label historical starting state, record completed care work, replace obsolete future roadmap with deferred boundaries |
| `backend/TASK.md` | Current freeze checklist; later implementation requires review |
| `frontend/README.md` | Include Care route and both reproducible browser commands |
| `docs/P2_3_STAFF_HEALTH_WORKSPACE.md` | Remove personal Maven-cache paths; distinguish historical script from current portable commands |
| `docs/P2_4A_CORRECTNESS_FIXES.md` | Portable Maven command; mark old display limitation as subsequently fixed |
| `docs/P2_4B_MILESTONE_FREEZE.md` | This report |

No migrations, production transaction boundaries, schema constraints, threshold, input validation or enum types were changed. The existing careQueryKeys helper already covers all care query consumers; there was no matching duplicated key literal to replace. No new query abstraction was introduced.

## Temperature presentation and proof

The two structured measurement displays now use `toFixed(2)`. The timeline visibly distinguishes **39.50 °C** from **39.51 °C**, with the same precision on the cat profile. Null remains “Not recorded.” Alert messages and human notes remain verbatim text; they are not reparsed as numeric fields.

The focused browser test verifies all four values 38.40, 39.50, 39.51 and 40.00 in the timeline, asserts the absence of a rounded `39.5 °C` measurement, and verifies 39.51 in the public profile. It runs at both 1440×1100 and 390×844. The real demo additionally shows persisted sample measurements as 38.30 and 40.00 in the timeline and 40.00 in the profile.

## PostgreSQL runtime evidence

`CatLockPostgresRuntimeIT` is explicitly separate from the default H2 suite. It uses the real BackendApplication, production Spring service proxies, JPA repositories and PostgreSQL 16.11 on this workstation. A new UUID schema is initialized through Flyway V1–V6, Hibernate runs `ddl-auto=validate`, and OSIV stays disabled. The database connection's current schema is asserted before any fixture deletion. Cleanup asserts Flyway's schema scope and removes only that run's generated schema.

Each case starts two independent service transactions. The first transaction explicitly holds only the Cat row lock before its business action; latches hold that transaction open while the second calls the competing service. A separate database query must observe the waiter's PID blocked by the owner's PID using `pg_blocking_pids`. Only then is the owner allowed to finish its business action and commit. All waits are bounded. This deliberately fixes the winning order and proves actual contention, rather than relying on thread scheduling.

| Case | Verified execution and persisted outcome |
| --- | --- |
| Competing approvals of two same-cat applications | First approval holds Cat; second approval waits. First succeeds; second returns the service's 409 conflict after commit. Exactly one APPROVED, the other REJECTED, Cat ADOPTED. |
| Competing resolutions of one Alert | First resolution holds Cat; second waits and then sees CLOSED/409. Exactly one CareRecord links to that Alert and Cat; timestamps agree; Cat NORMAL. |
| Health write waiting behind resolution | Resolution owns Cat; incoming 40.00 observation waits. Resolution commits CLOSED and one linked CareRecord; health write then creates a new OPEN alert, preserving the old CLOSED alert and returning Cat to UNDER_OBSERVATION. Both operations succeed in this legal serial order. |

The helper's 200/409 outcomes represent service success or ResponseStatusException; these runtime tests do not use HTTP controllers. Existing H2 MockMvc and live browser checks cover HTTP behavior separately.

**Proven:** these three coordinated transaction histories use the shared Cat lock on PostgreSQL; waiting services observe the committed state and preserve the asserted persisted invariants; current migrations can initialize a schema accepted by Hibernate validation.

**Not proven:** all interleavings, the reverse health/resolution order, load capacity, throughput/latency, fairness, deadlock freedom across hypothetical new workflows, distributed locking, external SQL writers, failover, production safety, human-decision freshness or strong multi-query timeline snapshots. No production-scale concurrency claim follows from three targeted tests.

## Commands and final results

Use Java 21, Maven 3.9+, Node.js 22.12+ and npm. Commands below assume the indicated project subdirectory. Normal Maven defaults are sufficient; this workstation's runs additionally used offline mode and its pre-existing dependency cache.

From `backend`:

```powershell
mvn clean test
```

For PostgreSQL, set the environment variables to a database account that can create schemas. The following matches the repository's local development database configuration; use your own values when different:

```powershell
$env:PAWTRACK_TEST_JDBC_URL = 'jdbc:postgresql://localhost:5432/pawtrack'
$env:PAWTRACK_TEST_DB_USER = 'pawtrack'
$env:PAWTRACK_TEST_DB_PASSWORD = 'pawtrack'
mvn "-Dtest=PostgresMigrationIT,CareRecordPostgresMigrationIT,AlertResolutionPostgresMigrationIT" test
mvn "-Dtest=CatLockPostgresRuntimeIT" test
```

PostgreSQL classes use the opt-in `*IT` naming convention and environment condition. They are not included in default `mvn clean test`; explicitly selecting them without the required URL skips them. This verification set the URL and recorded zero skips. Migration tests cover V3→V4, V4→V5 and V5→V6 separately; their existing assertions and SQL were not changed.

From `frontend`:

```powershell
npm ci
npx playwright install chromium
npm run build
npm run lint
npm run test:care
npm run test:live
```

| Final check | Result |
| --- | --- |
| Complete backend default `clean test` | **63 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| Existing PostgreSQL migration tests | **3 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| Separate PostgreSQL runtime tests | **3 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| Frontend production build | **Passed** |
| Frontend lint | **Passed** |
| `test:care`, mocked APIs | **12 passed**, Chromium desktop/narrow, no retries |
| `test:live`, real backend/frontend | **1 passed**, both vertical slices plus narrow Care, no retries |

The default suite preserves all P1/P2 adoption, concurrency, rollback, timeline, precision, resolution and demo/bootstrap checks. PostgreSQL results are reported separately, not added to the default suite count. The final database inventory showed no remaining schemas matching the four test prefixes.

## Live demo verification

`npm run test:live` requires free ports **19091** and **5173**. Playwright starts its own demo backend on 19091, with a fresh disposable H2 database, then Vite on 5173 proxying to it. The existing CORS configuration accepts this frontend origin. It never reuses an existing backend; it has no API route interception. Test writes use sample identities and belong only to this temporary demo.

Verified through the UI:

1. Browse Mochi, submit Freeze Alex and Freeze Sam applications, and view pending receipts.
2. Approve Alex in staff review; verify Alex APPROVED, Sam REJECTED and Mochi in the found-home state/filter.
3. Open Nori: OPEN fever alert, public application unavailable, latest measurement 40.00 °C.
4. Open Care alerts: real timeline shows OPEN plus 38.30/40.00 observations.
5. At 390×844, enter a note and resolve; verify CLOSED, the new note and its linked Alert reference, empty OPEN queue, and no horizontal overflow.
6. At desktop width, retain that updated timeline; return to Nori's public profile and verify the application form is available. A read of the real dashboard confirms NORMAL and no active alert.

No browser page errors occurred. Full-page narrow/open and desktop/closed screenshots were captured and visually inspected under the ignored `frontend/test-results/live` output directory.

On this workstation, the first chosen backend port 19090 was owned by Docker, so the runner was changed to free port 19091. The existing Vite server on 5173 was briefly paused for the final run and restored afterward. The existing demo backend on 9090 was not restarted or modified. Afterward, the restored frontend proxy returned the existing six-cat dataset and the temporary 19091 listener was gone.

Local logs (ignored): `backend/p2-4b-test.log`, `backend/p2-4b-migrations.log`, `backend/p2-4b-runtime.log`, `frontend/p2-4b-care.log`, `frontend/p2-4b-live.log`. Screenshot output is regenerated by the live command.

## Cleanup and deferred scope

Source cleanup removes unused starter content and obsolete migration advice without changing executable backend logic. Current README/architecture/checklist distinguish completed P1/P2 work from historical milestones. Personal user/cache paths were removed from public instructions; previous validation counts remain labeled as historical. The architecture now accurately identifies which services assemble DTOs transactionally and acknowledges the weaker consistency of multi-query reads.

Intentionally deferred: mixed Cat.status, manual hold semantics, pagination, consistent eligibility display for unusual manual status changes, stronger timeline snapshots, stale-decision protection, database constraints for direct SQL writers, upload hardening, authentication/ownership, AI/ML, IoT, notifications, charts, microservices/Kafka/Redis, real-time streaming and deployment. None was implemented, and none is a new prerequisite silently added to this freeze phase.

P2.4b stops here. Freeze approval is the remaining review step; this report does not start a later milestone.
