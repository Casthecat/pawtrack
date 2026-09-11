# P5.2: Single-origin Render portfolio demo

This is deployment configuration for a **public portfolio demo**, not a production service. No Render resources have been created by this implementation. Never enter real applicant/contact/medical data. Anyone can use the documented fake STAFF/ADOPTER accounts; account ownership separates demo personas, not individual visitors sharing those credentials.

## Build and routing

The root `Dockerfile` has three stages: Node 22 builds the locked React frontend; Maven 3.9.12/Java 21 copies that build into `src/main/resources/static` and packages Spring Boot; the final Java 21 JRE image contains the executable jar and a writable upload directory. It runs as the unprivileged `pawtrack` user with one Java process, no Node/Vite/Maven runtime and no persistent disk. Maven packaging skips tests because regression gates run separately in CI.

`.dockerignore` excludes workstation environment files, dependencies, logs, build output, browser artifacts, uploads and local tools. The frontend build uses an empty `VITE_API_BASE_URL`, so API and image URLs are relative to the page origin. Existing Vite development proxies remain unchanged.

Spring serves real static files first. Missing extensionless client routes fall back to `index.html`, including deep links such as `/cats/1`, `/applications/1` and `/staff/care`. The resolver never substitutes the SPA for API, uploads, actuator, developer-doc or error paths, or missing files containing an extension. Existing API controller mappings and the deny-by-default security chain remain authoritative. `/uploads/**` still uses its separate validated local-file resource handler.

## Render Blueprint and environment

`render.yaml` defines one Docker Web Service plus one disposable PostgreSQL 16 database in Oregon. The service follows `main` and uses `autoDeployTrigger: checksPass`. It listens on `0.0.0.0:${PORT}` (`10000` fallback), and Render checks `/actuator/health`. Health exposes status only. Both resources request the free plan; eligibility/availability and limits must be checked in the Render dashboard before creation. The database has `ipAllowList: []` so access uses Render's private network rather than an open external database endpoint.

Required environment values are supplied by the Blueprint:

- `SPRING_PROFILES_ACTIVE=demo-cloud` (the only active profile).
- `PAWTRACK_DEMO_DATABASE_ACK=I_ACKNOWLEDGE_THIS_DATABASE_IS_DISPOSABLE`. This is an explicit safety acknowledgement, not a secret.
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` from the Blueprint database's properties. Render generates the database password; none is stored in Git.
- Render supplies `PORT`. `DB_PORT` defaults to 5432 outside the Blueprint.

The profile constructs `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` and enables Flyway with clean disabled, followed by Hibernate `validate`. Existing migrations V1–V10 are unchanged. There is no create-drop, automatic data deletion, baseline shortcut or alternative cloud schema. Default/local configuration is preserved.

## Known-password safety

The existing `demo` profile retains its original pre-initialization H2-only/create-drop guard and actual JDBC checks. Its fixture insertion code is shared with the cloud seeder without changing those checks.

A separate `demo-cloud` guard runs before Flyway/DataSource/JPA initialization. It requires the explicit acknowledgement above, PostgreSQL driver, a PostgreSQL URL naming `pawtrack_portfolio_demo` (or the same name plus a 32-character hexadecimal disposable-test suffix), no URL query/user-info/fragment, Flyway enabled and Hibernate validate. Mixing this profile with `demo`, `production`, `test` or any other profile is rejected. Before creating accounts, the runner also checks the actual JDBC product, URL and catalog. Ordinary default/production/test profiles do not register either cloud provisioning runner or cloud data fixtures.

The database name convention plus acknowledgement prevents accidental provisioning into ordinary database configurations; it is not proof that an operator has kept real data out of an intentionally acknowledged database. Reserve this database solely for disposable portfolio data. The deployment must never be repurposed for real applicants.

The existing three encoded fake accounts and six-cat/Nori-care fixtures are reused. Missing accounts are inserted; passwords are not reset on restart. Cats/care fixtures are inserted only when the cat table is empty. There is no new registration endpoint or product workflow.

## HTTPS sessions and CSRF

The hosted profile sets Secure session cookies while retaining HttpOnly, SameSite=Lax, cookie-only tracking and the existing session timeout. `server.forward-headers-strategy=framework` handles Render's HTTPS termination so Spring sees the original scheme/host and CSRF cookies inherit secure requests. This relies on the hosting proxy supplying trusted forwarded headers; the container's backend should not be exposed independently of that proxy in a real deployment.

No CSRF, role, ownership or API matcher is disabled or loosened. No cross-origin credential transport is added. Local demo HTTP cookie behavior is unchanged. Sessions remain in memory and end on application restart; distributed sessions are out of scope.

## Uploads and reset strategy

Validated JPEG/PNG upload is unchanged. The runtime stores files in `/app/uploads` on Render's **ephemeral** filesystem. Expect them to disappear on replacement, redeploy or restart; never rely on persistence. Database image paths can outlive their files, and the existing frontend image fallback handles missing portraits. The UI explicitly calls uploaded photos temporary.

A normal restart/redeploy preserves PostgreSQL adoption/care state, but loses in-memory sessions and may lose uploads. It is **not** a database reset. The smallest full reset is manual recreation of this dedicated demo database, followed by service redeploy. A fresh database receives V1–V10 and the original fixtures. There is no reset endpoint, cron, scheduler, object storage or persistent disk.

For a reset: suspend the demo Web Service; verify that `pawtrack-demo-db` is exclusively disposable; delete only that demo database instance in Render; sync the Blueprint to recreate it with `databaseName: pawtrack_portfolio_demo`; resync the service's database references and redeploy/resume the service. Recheck six cats, the three fake accounts and Nori's open care alert. Never apply this reset procedure to development or real data.

## Manual Render deployment steps

1. Review/merge these changes into `Casthecat/pawtrack` on `main`. Wait for all existing CI jobs plus the new cloud-profile PostgreSQL step to pass.
2. In Render, choose **New → Blueprint**, connect the GitHub repository and select the root `render.yaml` from `main`. Keep the Docker context at the repository root; do not set the root directory to `backend` or `frontend`.
3. Review the two resources, region and free-plan availability before creating anything. No service disk is configured. Verify the dedicated database name and acknowledgement. If a free database is unavailable, resolve that dashboard/account constraint explicitly rather than silently selecting a paid plan or reusing an unrelated database.
4. Create/sync the Blueprint. Verify the service environment references are populated with the new database values, the profile is only `demo-cloud`, and the health path is `/actuator/health`. Do not paste a raw Render `postgresql://...` connection string into Spring's JDBC URL.
5. Watch the initial Docker build/startup and health check. Confirm Flyway reaches V10 and Hibernate validation succeeds. Open the service's assigned **HTTPS** `onrender.com` URL; no public hostname is assumed or invented in this repository.
6. Check direct page loads/refreshes for `/cats/1`, `/login`, `/applications` and `/staff/care`. Use the existing fake accounts from the README to run public browsing, adopter application/list/receipt, other-adopter 404, STAFF review/care resolution and logout. In browser network tools, verify API/media requests use the same host, cookies are Secure, and CSRF-protected writes still work.
7. Upload a small JPEG/PNG as STAFF and confirm its public portrait loads. Confirm ADOPTER cannot upload. Treat the uploaded file as temporary and use only fake inputs.

Render free services may cold-start after inactivity, and free PostgreSQL has expiry/availability limits. Verify the current terms in [Render free hosting documentation](https://render.com/docs/free); this is not an uptime or durability promise. See also the [Blueprint reference](https://render.com/docs/blueprint-spec), [Docker deployment](https://render.com/docs/docker) and [health checks](https://render.com/docs/health-checks).

## Verification and limits

Deployment-specific automated coverage:

- `SpaRoutingIntegrationTest`: real static content/deep-link fallback, JSON API routing, unclassified API denial, and missing upload/asset 404 responses.
- `CloudDemoAccountSafetyTest`: accepted disposable configuration, refusal of ordinary/unsafe URLs and missing safeguards, profile isolation, and actual-connection mismatch before account writes.
- `DemoCloudPostgresIT`: creates a randomly named disposable database, starts the real cloud profile, verifies V10/encoded fixtures/forwarded HTTPS cookies and CSRF, then restarts to prove database state and account hashes survive. It drops only its generated database. Run with the existing `PAWTRACK_TEST_JDBC_URL`, `PAWTRACK_TEST_DB_USER`, `PAWTRACK_TEST_DB_PASSWORD` and `mvn -Dtest=DemoCloudPostgresIT test`. Unlike the older schema-only tests, its isolated admin connection needs CREATEDB; the CI PostgreSQL service user has this capability. Do not grant it to normal development users just for this test.

The PostgreSQL CI job retains every historical migration and lock test, and adds the cloud test as a separate step. Default H2 and both existing browser suites remain intact.

The independent `docker-image` job runs on Ubuntu 24.04, checks out the repository and executes `docker build --file Dockerfile --tag pawtrack:ci .` from its root. It starts that image with the unchanged `demo-cloud` profile against a healthy disposable PostgreSQL 16 service named `pawtrack_portfolio_demo`, then requires `/actuator/health` to return `UP`. The Linux container uses host networking to reach the CI service's published port. Failed builds or startup checks fail the job; container logs are printed on failure and the application container is removed with `always()` cleanup. No registry login, push, deployment token or real database credentials are used. Database credentials are ephemeral CI fixtures. Cross-run Docker layer caching is omitted to keep this first build gate simple; no Dockerfile or application behavior was changed for the gate.

Local verification on 2026-09-10 (Windows, Java 21, Maven 3.9.11, Node 24.11.1):

- Complete `mvn clean test`: **174 passed**, zero failures/errors/skips (150 existing plus 24 focused routing/safety checks).
- All seven historical PostgreSQL migration classes: **12 passed**. `CatLockPostgresRuntimeIT`: **4 passed**. `DemoCloudPostgresIT`: **1 passed**, including same-origin Origin and forwarded HTTPS headers.
- `npm ci`, `npm run build`, `npm run lint`: passed. Existing `npm run test:care`: **34 passed**; unchanged `npm run test:live`: **1 passed** against real Vite/backend services.
- Packaged the actual React production assets into the executable jar and reran the unchanged live browser scenario directly against that jar, with no Vite/API interception: **1 passed**. The local-only Playwright configuration used the existing safe H2 demo profile on port 19092. This proves packaged single-origin routing/workflows over local HTTP; the separate PostgreSQL test covers cloud configuration and forwarded HTTPS behavior. Test-only static fixtures were confirmed absent from the jar.
- `render.yaml` passed validation against Render's official JSON Schema; updated CI passed actionlint. All three Docker base tags were confirmed on Docker Hub. Git diff checks passed; V1–V10, the authorization chain and dependency lockfiles are unchanged. Generated files/tools/logs remain ignored.
- The existing local PostgreSQL account lacked CREATEDB, so it was left unchanged. Cloud/migration/runtime verification used a fresh loopback-only PostgreSQL 16 instance under ignored local files, with disposable schemas/databases; generated cloud databases were dropped and the test server stopped. Application/browser/test ports were released afterward.

Docker image gate verification: the repository-root `docker build` completed successfully with the unchanged Dockerfile. The final Linux image ran as UID/GID 999 (`pawtrack`) against a fresh PostgreSQL 16 container using `demo-cloud`; Flyway applied all ten migrations and `/actuator/health` returned `UP`. The packaged SPA deep link and six seeded cats were also verified. Local Docker Desktop used a task-specific bridge network and loopback port 19093; the Ubuntu CI job uses host networking to reach its published PostgreSQL service port. Both local containers, their anonymous volumes and the test network were removed afterward. No image was pushed. YAML/actionlint and extracted Bash syntax checks passed.

**Not verified:** this P5.2 revision and its new Docker job have not been pushed or executed on GitHub-hosted runners, and no Render deployment was performed. First hosted CI/deploy must still confirm the runner's service networking and Render forwarded headers/TLS/cookies, free-plan memory, health checks, cold starts, Blueprint synchronization and reset/upload-loss behavior.

`npm ci` still reports the existing 20 dependency vulnerabilities recorded in P5.1; no dependency upgrade or automatic fix was included in deployment configuration.

No deployment was performed, no provider other than Render was added, and no production-security, full penetration-test, production load, real-data privacy or durability guarantee is claimed. Object storage, production configuration, registration and later milestones remain deferred.

## Files changed

- Deployment: `Dockerfile`, `.dockerignore`, `render.yaml`, `backend/src/main/resources/application-demo-cloud.yml`.
- SPA routing: `backend/src/main/java/com/pawtrack/backend/config/WebConfig.java`, `SpaResourceResolver.java` in the same package.
- Guarded fixtures: `backend/src/main/java/com/pawtrack/backend/identity/config/CloudDemoAccounts.java`, `DemoAccountFixtures.java`, `DemoAccounts.java`; `backend/src/main/java/com/pawtrack/backend/config/DemoData.java`.
- Focused tests: `backend/src/test/java/com/pawtrack/backend/config/SpaRoutingIntegrationTest.java`; `backend/src/test/java/com/pawtrack/backend/identity/config/CloudDemoAccountSafetyTest.java`, `DemoCloudPostgresIT.java`; tiny fixtures `backend/src/test/resources/static/index.html` and `static/assets/probe.js`.
- CI: `.github/workflows/ci.yml` adds the separate cloud PostgreSQL check and independent Docker image build/startup job.
- Notices/docs: `frontend/src/App.tsx`, `frontend/src/pages/LoginPage.tsx`, `README.md`, this report.
