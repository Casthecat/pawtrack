# P5.1: Continuous Integration

P5.1 adds only `.github/workflows/ci.yml`, this report, and a short README CI section/status badge. Application code, security configuration, test logic, Playwright configuration, dependencies and migrations V1–V10 are unchanged.

## Workflow and triggers

`CI` runs on pull requests and pushes to `main`. Feature-branch pushes without a PR do not run another expensive copy. Superseded runs for the same workflow/ref are cancelled. There are no schedules, release jobs or deployments.

Four independent jobs use GitHub-hosted `ubuntu-24.04`, each with a 15-minute limit. The token has only `contents: read`; checkout does not persist credentials. No repository secrets are required.

## What each job proves

- **backend:** Java 21 (Temurin), then `bash ./mvnw --batch-mode --no-transfer-progress clean test` from `backend`. This is the complete default isolated regression suite, mostly H2. The opt-in PostgreSQL `*IT` classes are deliberately run in the separate job below.
- **frontend-browser:** Node 22, `npm ci`, `npm run build`, `npm run lint`, `npx playwright install --with-deps chromium`, then `npm run test:care`, all from `frontend`. These are deterministic frontend state/UX checks against mocked APIs. Existing desktop/narrow projects, two workers and zero retries remain unchanged.
- **postgres:** Java 21 and a disposable PostgreSQL 16 service. Separate Maven steps verify historical schema upgrades/validation and targeted real row-lock execution histories. These are distinct evidence categories.
- **live-demo:** Java 21, the hosted runner's Maven on PATH, Node 22, `npm ci`, Chromium with Linux dependencies, and `npm run test:live`. The existing runner starts fresh H2 demo data and Vite, then exercises real session authentication, CSRF, role checks, application ownership, adoption review and care resolution. There is no API interception.

The wrapper pins Maven 3.9.12; CI invokes it through `bash` because its existing Git mode is `100644`. The live runner retains its existing platform-specific `mvn`/`mvn.cmd` command. Ubuntu 24.04 includes Maven; the live job prints its version before starting. Local contributors can continue using the individual Maven/npm commands in the README.

## PostgreSQL verification

The service uses `postgres:16`, database/user/password `pawtrack`, and host mapping `5432:5432`. These are ephemeral CI-only values. `pg_isready -U pawtrack -d pawtrack` runs every 10 seconds with a 5-second timeout and five retries before job steps start.

The job sets:

```text
PAWTRACK_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/pawtrack
PAWTRACK_TEST_DB_USER=pawtrack
PAWTRACK_TEST_DB_PASSWORD=pawtrack
```

All seven historical migration classes run together, without omitting older phases:

```sh
# From backend; use mvnw.cmd or installed mvn on Windows.
bash ./mvnw --batch-mode --no-transfer-progress -Dtest=PostgresMigrationIT,CareRecordPostgresMigrationIT,AlertResolutionPostgresMigrationIT,CatStatusPostgresMigrationIT,RemoveLegacyCatStatusPostgresMigrationIT,UserAccountPostgresMigrationIT,AdoptionOwnershipPostgresMigrationIT test
```

Each retains its own disposable schema and cleanup. A separate step runs:

```sh
bash ./mvnw --batch-mode --no-transfer-progress -Dtest=CatLockPostgresRuntimeIT test
```

The four coordinated histories are competing approvals, competing alert resolutions, a health write waiting behind resolution, and same-owner duplicate submission for one Cat. The existing tests confirm actual contention using `pg_blocking_pids` before releasing the Cat lock. They do not certify every possible concurrency history or production load capacity.

## Live process lifecycle and artifacts

The unchanged Playwright `webServer` configuration owns both the Maven/backend and Vite process trees, refuses server reuse, and waits for backend health on port 19091 and Vite on 5173. Inspection of the installed Playwright 1.62.1 implementation confirms process-group termination on Linux and tree termination on Windows, including test failure and startup failure teardown. No additional background shell launcher or Docker wrapper was introduced.

The live job has an `always()` step that binds and releases both ports after Playwright exits. A retained listener fails the job. Playwright performs cleanup; the check provides observable evidence that the server ports were released on success or failure.

Both browser jobs upload `frontend/test-results/` only on failure, if files exist. The artifacts have distinct names (`mocked-browser-failure`, `live-demo-failure`) and seven-day retention. Existing failure screenshots and retained traces are included; the live test's walkthrough screenshots are included when generated. Successful runs upload nothing. Existing Git ignore rules exclude results, logs, build output, uploads and local tools; demo H2 data lives only in memory.

## Caches

`setup-java` caches Maven dependencies keyed by `backend/pom.xml`. `setup-node` caches npm downloads keyed by `frontend/package-lock.json`; every frontend job still runs `npm ci`. Build output, H2/PostgreSQL data, Playwright results and browser binaries are not cached. Chromium and required Linux packages are installed using Playwright's standard CI command.

## Local verification and hosted-runner limits

Local verification completed on 2026-09-10 using Windows, Java 21, Maven 3.9.11 and Node 24.11.1 (within the documented Node 22.12+ requirement); CI selects Node 22. Tests used the existing local PostgreSQL service and isolated test schemas, rather than a GitHub service container.

- `mvn clean test`: **150 tests passed**, zero failures/errors/skips. Local execution used installed Maven with its populated dependency cache in offline mode; CI uses normal online dependency resolution.
- All seven explicit PostgreSQL migration classes: **12 tests passed**, zero failures/errors/skips, including the historical upgrades through V10.
- `CatLockPostgresRuntimeIT`: **4 tests passed**, zero failures/errors/skips, including same-owner contention and one final PENDING application.
- `npm ci`: passed; the existing lockfile remained unchanged. `npm run build` and `npm run lint`: passed.
- `npx playwright install chromium`: passed. Linux system dependency installation is part of CI and cannot be exercised on this Windows host.
- `npm run test:care`: **34 passed**, with the existing desktop/narrow projects and no retries.
- `npm run test:live`: **1 passed**, real stack with no interception; both ports were released afterward.
- A separate `npm run test:live -- --timeout=1` intentionally failed after both servers started. Exit code 1 and subsequent successful binds to both ports confirmed failure cleanup. This probe is not counted as a failing regression or added to CI; it changes only that invocation's timeout. It expired before browser context setup completed, so it did not produce a screenshot/trace.
- YAML parsed successfully with the installed `js-yaml`; actionlint **1.7.12** reported no workflow errors. The validator was downloaded from its official release, verified against its SHA-256 checksum, and kept only under ignored `.local/`. `bash -n backend/mvnw` passed.
- Diff checks confirmed no generated artifacts, personal absolute paths in CI, migration changes, or backend/frontend application, dependency or test changes. Logs are local ignored `p5-1-*.log` files, not CI artifacts.

The workflow has not been pushed or executed on GitHub Actions in this change. A first GitHub-hosted run is still needed to confirm Ubuntu/Node 22 execution, fresh hosted dependency downloads, service health-check integration, cache restore/save, artifact upload and cancellation behavior. The README badge targets the actual `Casthecat/pawtrack` workflow; it is a live status link, not a claim that a hosted run has already passed.

## Boundaries and deferred work

This automates the existing portfolio evidence. It does not establish production load capacity, full browser compatibility, production deployment or a complete security audit. CSRF, session/role/ownership checks, API deny-by-default, demo account isolation and business transactions are unchanged.

No production credentials, insecure CI profile, deployment/provider choice, release publishing, infrastructure, coverage gate, dependency scanner or product feature is added. `npm ci` reported 20 existing dependency vulnerabilities (2 low, 4 moderate, 14 high); no dependency update or automatic audit fix was applied in this CI-only phase. Dependency remediation needs separate review.

P5.1 stops here. Deployment, production configuration and P5.2 remain deferred.

Implementation references: [Playwright CI](https://playwright.dev/docs/ci), [Playwright web servers](https://playwright.dev/docs/test-webserver), [Ubuntu 24.04 runner tools](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md), [setup-java Maven cache](https://github.com/actions/setup-java/tree/v5), and [setup-node npm cache](https://github.com/actions/setup-node/tree/v6).
