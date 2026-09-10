# PawTrack

PawTrack is a portfolio project for shelter cat care and adoption. It connects health observations with adoption decisions: an open care alert pauses adoption, and approving an application updates the cat and competing applications in one transaction.

P1/P2 is frozen at `v1.0-portfolio-core`. P3 split Cat health and adoption state; P4.1/P4.2 add server-session authentication, STAFF-only operations and SPA login/logout. P4.3 binds new applications to ADOPTER accounts and adds private receipts and My applications. P4.4 freezes this authorization milestone with API deny-by-default, validated local image uploads and isolated demo credentials. Public registration and production deployment security remain deferred.

## Run the local demo

Requirements: JDK 21, Maven 3.9+ (or the included Maven wrapper), Node.js 22.12+ and npm.

Check `java -version` and `mvn -version`: both should use Java 21. Set `JAVA_HOME` to your installed JDK 21 directory if they differ.

From the `pawtrack` directory, open two terminals.

Backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Frontend:

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Open [PawTrack](http://127.0.0.1:5173). The demo binds the backend to loopback port 9090 and creates six sample cats in a separate in-memory H2 database. Restarting the backend resets all demo data. It does not use the development PostgreSQL database. Photos use a neutral illustration when no image is available.

Sign in at `/login` to access Staff workspace. These **DEMO-ONLY** accounts require the `demo` profile, a named in-memory H2 datasource and `create-drop`, with encoded passwords. Persistent demo datasource configuration is rejected before JPA initialization; the seeder also checks the actual database connection:

- STAFF: `staff@example.com` / `PawTrack-demo-staff!`
- ADOPTER: `adopter@example.com` / `PawTrack-demo-adopter!`
- Second ADOPTER: `other-adopter@example.com` / `PawTrack-demo-other!`

Anonymous visitors can browse cats. Sign in as ADOPTER to apply using the account's name/email and view **My applications** (`/applications`). Each adopter can read only their own receipts; STAFF can review all applications. Historical applications retain a NULL owner and remain staff-only, even when their stored email matches an account. No registration or application-claim feature exists. Use these fictional demo identities; production deployment security is still deferred. See the [P4.3 inventory](docs/P4_3_ADOPTION_INVENTORY.md) and [ownership quality report](docs/P4_3_ADOPTER_OWNERSHIP.md).

## Five-minute walkthrough

**A. Adoption review**

1. Browse the gallery, search for Mochi, and open the profile.
2. Choose **Sign in to apply**, log in as demo ADOPTER and submit optional notes. Save the owned receipt; check **My applications**.
3. Sign out, then sign in as the second ADOPTER from Mochi’s page and submit a competing application. The first receipt must be inaccessible to this account.
4. Sign out, open `/staff`, sign in as demo STAFF, choose **Approve**, and confirm the decision.
5. Verify that the pending queue clears, the selected application becomes approved, the other becomes declined, and Mochi appears under **Found a home**.

**B. Care alert resolution**

1. Open Nori's public profile and note that applications are paused.
2. Open **Staff workspace → Care alerts** (`/staff/care`). Select Nori's OPEN fever alert.
3. Inspect the health timeline, including the recorded temperature, alert, and existing care note.
4. Choose **Checkup** and enter `Temperature rechecked; resting comfortably.`
5. Select **Resolve care alert**. Nori leaves the OPEN queue; the same timeline remains visible with the CLOSED alert and new linked care record.
6. Select **View cat profile** and verify NORMAL / AVAILABLE with no active alert. The application form is available to ADOPTER accounts again; STAFF sees an adopter-account requirement.

Restart the demo backend and reload the browser page to restore the initial six-cat dataset and Nori's OPEN alert. Use **Refresh** for updates from another session within the same demo run. Resolving an already closed alert shows a conflict and refreshes the history; a failed request keeps the draft note for retry.

Approval represents completion of adoption in this milestone. Interviews, handover, and signing are not modeled yet.

## Stack and boundaries

- Java 21, Spring Boot 3.3.6, Spring Data JPA, Bean Validation.
- PostgreSQL with Flyway V1–V10 for persistent development; H2 for isolated tests and the local demo.
- React, TypeScript, Vite, React Query, React Router, Axios, Tailwind CSS.
- A modular monolith organized by feature: `cat`, `healthdata`, `alert`, `adoption`, `care`, `identity`.

See [Architecture](backend/ARCHITECTURE.md), [Milestone review](docs/PROJECT_REVIEW.md), and [milestone checklist](backend/TASK.md).

## Behavior worth demonstrating

- Only pending applications can be approved or rejected. Repeated or reversed decisions return HTTP 409.
- Submission and approval check availability and open alerts on the backend.
- A pending application cannot be duplicated for the same cat and adopter account.
- Approving one application marks the cat adopted and declines other pending applications atomically.
- A pessimistic lock on the cat serializes competing reviews, submissions, and health/status writes through these services.
- Repeated fever observations reuse the existing open fever alert; a new alert can be created after closure.
- Health monitoring preserves an adopted cat's adoption state.
- A read-only health timeline combines observations, alerts, and human-entered care records as explicit DTO events ordered newest-first.
- Resolving an alert records a linked care action under the same Cat lock used by ingestion and adoption. Resolving the last open alert restores UNDER_OBSERVATION to NORMAL; other cat statuses are preserved.
- Responses are DTOs, validation failures return field errors, and the frontend distinguishes loading, empty, conflict, and failure states.

The current health rule is an inherited demonstration threshold (`temperatureC > 39.5`), not an AI model or a validated clinical decision system.

Latest health observations use `ts DESC, id DESC`. Optional temperature input matches `NUMERIC(5,2)`; unsupported precision/capacity returns 400 without rounding. Timeline and profile measurements display two decimals (39.50 and 39.51 remain distinct). Staff must successfully load the timeline before resolving; queue refreshes preserve the selected context and unsent note.

This milestone does not claim production authentication, production-scale concurrency, real IoT deployment, validated medical support, AI/ML, microservices, Kafka or real-time alert streaming.

## API for this milestone

Staff review/management/health-care routes require a STAFF session: anonymous requests return JSON 401 and ADOPTER requests return JSON 403. Authenticated writes also need a valid CSRF header; the SPA initializes and refreshes it centrally. Public cat reads/media remain available. POST `/api/adoptions` requires ADOPTER and accepts only `catId` plus optional `notes`; account identity comes from the authenticated principal ID. GET `/api/adoptions/{id}` requires STAFF or the owning ADOPTER; non-owners receive the normal 404. GET `/api/me/adoptions` is ADOPTER-only, ordered `createdAt DESC, id DESC`. All current API methods/paths are explicitly classified, then `/api/**` is denied for every role. Non-API SPA/static routing remains public. See the [final route inventory](docs/P4_4_ROUTE_INVENTORY.md) and [P4 freeze report](docs/P4_4_AUTHORIZATION_FREEZE.md).


| Method | Path | Purpose |
| --- | --- | --- |
| GET | /api/cats | Cat gallery |
| GET | /api/cats/{id}/dashboard | Cat details, latest temperature, open-alert indicator |
| GET | /api/cats/{id}/health-timeline | Newest-first observations, alerts, and care records for one cat |
| GET | /api/alerts?status=OPEN | Global care queue; defaults to OPEN, also accepts CLOSED |
| PATCH | /api/alerts/{id}/resolve | Resolve an OPEN alert with required careType and note |
| POST | /api/cats | Add a cat by name |
| POST | /api/adoptions | ADOPTER: submit catId and optional notes; identity from account |
| GET | /api/adoptions?status=PENDING | Review queue; omit status to list all |
| GET | /api/adoptions/{id} | Owner ADOPTER or STAFF: receipt and latest status |
| PATCH | /api/adoptions/{id}/approve | Approve a pending application |
| PATCH | /api/adoptions/{id}/reject | Reject a pending application |

STAFF session required for interactive API documentation: [Swagger UI](http://127.0.0.1:9090/swagger-ui/index.html).

P2.2 replaces the old body-less `/api/alerts/{id}/close` endpoint. Send `{"careType":"CHECKUP","note":"Temperature rechecked; resting comfortably."}` to `/resolve`. The response identifies `alertId`, `catId`, `status: CLOSED`, `resolvedAt`, and `careRecordId`. Notes must be non-blank and at most 2000 characters. Repeating a resolution returns 409 and creates no additional record. See [P2.2 implementation and validation](docs/P2_2_ALERT_RESOLUTION.md) for the full contract and changed-file inventory.

## Validation

```powershell
cd backend
mvn clean test
```

The default suite uses H2 and includes simultaneous approvals, invalid input, duplicate applications, invalid transitions, care-alert blocking, alert resolution, alert deduplication, browser CORS requests, care timeline assembly, and deterministic alert queue DTOs with open-in-view disabled.

```powershell
cd frontend
npm run build
npm run lint
npx playwright install chromium
npm run test:care
```

P4 freeze verification: 150 default backend tests; frontend build/lint; 34 Chromium checks at 1440×1100 and 390×844; 12 PostgreSQL migration checks and 4 Cat-lock runtime cases. `test:care` starts Vite on free port 5175 with mocked APIs and no backend. See the [P4.4 freeze report](docs/P4_4_AUTHORIZATION_FREEZE.md) for current results and evidence limits. Earlier milestone reports are historical records.

Optional PostgreSQL migration tests use separate randomly named schemas. The P1 test verifies V3→V4 adoption metadata; P2.1 verifies V4→V5 care records; P2.2 verifies V5→V6 resolution metadata, legacy data, and optional alert links. P3 covers typed states and legacy-column removal; P4 covers identity and V9→V10 nullable ownership, FK/index behavior and Hibernate validation. Each test validates its data and removes only its own schema:

```powershell
$env:PAWTRACK_TEST_JDBC_URL = 'jdbc:postgresql://localhost:5432/pawtrack'
$env:PAWTRACK_TEST_DB_USER = 'pawtrack'
$env:PAWTRACK_TEST_DB_PASSWORD = 'pawtrack'
mvn "-Dtest=PostgresMigrationIT,CareRecordPostgresMigrationIT,AlertResolutionPostgresMigrationIT,CatStatusPostgresMigrationIT,RemoveLegacyCatStatusPostgresMigrationIT,UserAccountPostgresMigrationIT,AdoptionOwnershipPostgresMigrationIT" test
```

With those environment variables still set, run the separate real Spring/JPA PostgreSQL checks from `backend`:

```powershell
mvn "-Dtest=CatLockPostgresRuntimeIT" test
```

This test creates a fresh UUID schema, runs Flyway V1–V10 and Hibernate schema validation, and cleans only that schema. The database user needs schema creation rights. Four coordinated cases verify competing approvals, competing resolutions, a health write waiting behind resolution, and same-owner concurrent application submission. Each checks `pg_blocking_pids` before releasing the Cat lock. This proves those specific service transaction histories on PostgreSQL; it does not establish load capacity, all possible interleavings, distributed correctness or decision freshness. PostgreSQL `*IT` classes are opt-in and excluded from the default H2 suite; without the required environment variable they skip.

For a live, unmocked demo regression, from `frontend`:

```powershell
npm run test:live
```

This starts its own fresh demo backend on 19091 and Vite on 5173, exercises both walkthroughs, and checks the narrow Care workspace. Both ports must be free; do not run a separate frontend on 5173 simultaneously. Java 21, Maven and the installed Playwright Chromium are required. The runner never reuses a running backend; its H2 data is disposable. Screenshot artifacts are in the ignored `frontend/test-results/live` directory.

## Frozen P4 security boundary

Implemented: session authentication, CSRF, STAFF authorization, ADOPTER-only submission, application ownership, owner-only My applications, API deny-by-default, validated JPEG/PNG upload and demo known-password isolation.

Existing STAFF image upload accepts JPEG/PNG only, up to 5 MiB (6 MiB multipart request), at most 4096 pixels per side and 16 megapixels. Java ImageIO checks and re-encodes content; original filenames are ignored and server UUID names stay under the configured upload root. Portraits remain public at `/uploads/**`. Invalid input returns JSON 400/413. This changes new uploads; it does not retroactively sanitize files placed in the directory outside this boundary.

Not claimed: public registration, email verification, password reset, OAuth/MFA, brute-force/rate-limit protection, distributed sessions, production TLS/proxy configuration, a full security audit/penetration test, or production deployment security. These require separate review; P4 ends here.

## Persistent development

Start PostgreSQL using `docker compose -f infra/docker-compose.yml up -d`, then run the backend **without** the demo profile. The default connection is `localhost:5432/pawtrack`; Flyway applies migrations and Hibernate validates the schema. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` to override local defaults.

For the persistent backend on port 8080, set `$env:PAWTRACK_API_TARGET = 'http://127.0.0.1:8080'` before starting Vite. If Windows reserves port 8080, pass `"-Dspring-boot.run.arguments=--server.port=9090"` to Maven instead and leave the proxy at its default.

The Vite development proxy forwards `/api` and `/uploads` to port 9090. For a separately hosted backend, set `VITE_API_BASE_URL` using `frontend/.env.example` and configure backend CORS. A production frontend host also needs an SPA fallback and API/media routing; `vite preview` is not the configured full-stack demo.
