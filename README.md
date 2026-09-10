# PawTrack

PawTrack is a portfolio project for shelter cat care and adoption. It connects health observations with adoption decisions: an open care alert pauses adoption, and approving an application updates the cat and competing applications in one transaction.

The P1/P2 milestone includes adoption review and staff care: open alert queue, health timeline, recorded care actions, and restored adoption availability when appropriate. P2.4a/b add correctness fixes and freeze verification. No later product phase is included; freezing this milestone is subject to review.

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

Staff review and application receipts are intentionally accessible in this local demo. Use sample names and example.com addresses. Authentication, ownership checks, and private media access must be implemented before public deployment or use with real applicants.

## Five-minute walkthrough

**A. Adoption review**

1. Browse the gallery, search for Mochi, and open the profile.
2. Submit an application using `alex@example.com`. Save the resulting application page.
3. Submit a second application for the same cat using `sam@example.com`.
4. Open **Staff workspace → Adoption review** (`/staff`), choose **Approve**, and confirm the decision.
5. Verify that the pending queue clears, the selected application becomes approved, the other becomes declined, and Mochi appears under **Found a home**.

**B. Care alert resolution**

1. Open Nori's public profile and note that applications are paused.
2. Open **Staff workspace → Care alerts** (`/staff/care`). Select Nori's OPEN fever alert.
3. Inspect the health timeline, including the recorded temperature, alert, and existing care note.
4. Choose **Checkup** and enter `Temperature rechecked; resting comfortably.`
5. Select **Resolve care alert**. Nori leaves the OPEN queue; the same timeline remains visible with the CLOSED alert and new linked care record.
6. Select **View cat profile** and verify that the application form is available again when no other blocking state remains.

Restart the demo backend and reload the browser page to restore the initial six-cat dataset and Nori's OPEN alert. Use **Refresh** for updates from another session within the same demo run. Resolving an already closed alert shows a conflict and refreshes the history; a failed request keeps the draft note for retry.

Approval represents completion of adoption in this milestone. Interviews, handover, and signing are not modeled yet.

## Stack and boundaries

- Java 21, Spring Boot 3.3.6, Spring Data JPA, Bean Validation.
- PostgreSQL with Flyway V1–V6 for persistent development; H2 for isolated tests and the local demo.
- React, TypeScript, Vite, React Query, React Router, Axios, Tailwind CSS.
- A modular monolith organized by feature: `cat`, `healthdata`, `alert`, `adoption`, `care`.

See [Architecture](backend/ARCHITECTURE.md), [Milestone review](docs/PROJECT_REVIEW.md), and [milestone checklist](backend/TASK.md).

## Behavior worth demonstrating

- Only pending applications can be approved or rejected. Repeated or reversed decisions return HTTP 409.
- Submission and approval check availability and open alerts on the backend.
- A pending application cannot be duplicated for the same cat and normalized email.
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

| Method | Path | Purpose |
| --- | --- | --- |
| GET | /api/cats | Cat gallery |
| GET | /api/cats/{id}/dashboard | Cat details, latest temperature, open-alert indicator |
| GET | /api/cats/{id}/health-timeline | Newest-first observations, alerts, and care records for one cat |
| GET | /api/alerts?status=OPEN | Global care queue; defaults to OPEN, also accepts CLOSED |
| PATCH | /api/alerts/{id}/resolve | Resolve an OPEN alert with required careType and note |
| POST | /api/cats | Add a cat by name |
| POST | /api/adoptions | Submit catId, adopterName, adopterEmail, optional notes |
| GET | /api/adoptions?status=PENDING | Review queue; omit status to list all |
| GET | /api/adoptions/{id} | Application receipt and latest status |
| PATCH | /api/adoptions/{id}/approve | Approve a pending application |
| PATCH | /api/adoptions/{id}/reject | Reject a pending application |

Interactive API documentation: [Swagger UI](http://127.0.0.1:9090/swagger-ui/index.html).

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

Freeze verification: 63 default backend tests; frontend build/lint; 12 Chromium care checks at 1440×1100 and 390×844. `test:care` starts Vite on free port 5175 with mocked APIs and no backend. See [P2.4b quality report](docs/P2_4B_MILESTONE_FREEZE.md) for final results and evidence limits. Earlier milestone reports are historical records.

Optional PostgreSQL migration tests use separate randomly named schemas. The P1 test verifies V3→V4 adoption metadata; P2.1 verifies V4→V5 care records; P2.2 verifies V5→V6 resolution metadata, legacy data, and optional alert links. Each test validates its data and removes only its own schema:

```powershell
$env:PAWTRACK_TEST_JDBC_URL = 'jdbc:postgresql://localhost:5432/pawtrack'
$env:PAWTRACK_TEST_DB_USER = 'pawtrack'
$env:PAWTRACK_TEST_DB_PASSWORD = 'pawtrack'
mvn "-Dtest=PostgresMigrationIT,CareRecordPostgresMigrationIT,AlertResolutionPostgresMigrationIT" test
```

With those environment variables still set, run the separate real Spring/JPA PostgreSQL checks from `backend`:

```powershell
mvn "-Dtest=CatLockPostgresRuntimeIT" test
```

This test creates a fresh UUID schema, runs Flyway V1–V6 and Hibernate schema validation, and cleans only that schema. The database user needs schema creation rights. Three coordinated cases verify competing approvals, competing resolutions, and a health write waiting behind resolution. Each checks `pg_blocking_pids` before releasing the Cat lock. This proves those specific service transaction histories on PostgreSQL; it does not establish load capacity, all possible interleavings, distributed correctness or decision freshness. PostgreSQL `*IT` classes are opt-in and excluded from the default H2 suite; without the required environment variable they skip.

For a live, unmocked demo regression, from `frontend`:

```powershell
npm run test:live
```

This starts its own fresh demo backend on 19091 and Vite on 5173, exercises both walkthroughs, and checks the narrow Care workspace. Both ports must be free; do not run a separate frontend on 5173 simultaneously. Java 21, Maven and the installed Playwright Chromium are required. The runner never reuses a running backend; its H2 data is disposable. Screenshot artifacts are in the ignored `frontend/test-results/live` directory.

## Persistent development

Start PostgreSQL using `docker compose -f infra/docker-compose.yml up -d`, then run the backend **without** the demo profile. The default connection is `localhost:5432/pawtrack`; Flyway applies migrations and Hibernate validates the schema. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` to override local defaults.

For the persistent backend on port 8080, set `$env:PAWTRACK_API_TARGET = 'http://127.0.0.1:8080'` before starting Vite. If Windows reserves port 8080, pass `"-Dspring-boot.run.arguments=--server.port=9090"` to Maven instead and leave the proxy at its default.

The Vite development proxy forwards `/api` and `/uploads` to port 9090. For a separately hosted backend, set `VITE_API_BASE_URL` using `frontend/.env.example` and configure backend CORS. A production frontend host also needs an SPA fallback and API/media routing; `vite preview` is not the configured full-stack demo.
