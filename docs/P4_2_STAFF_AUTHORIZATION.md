# P4.2 Staff Authorization and Authentication UI — quality report

Verified 2026-09-10 on `feat/p4-auth`. P4.2 implements STAFF authorization and SPA session authentication. It does not complete application ownership or production security. No migration, domain redesign, Cat lock change, registration or staff-action attribution was introduced.

## 1. Complete route inventory and classification

The [route inventory](P4_2_ROUTE_INVENTORY.md) was recorded before modifying SecurityFilterChain, after inspecting all seven controllers, security handlers, WebConfig and management configuration. Its complete classification is:

- PUBLIC, CatController: GET `/api/cats`, GET `/api/cats/{id}`, GET `/api/cats/{id}/dashboard`. Dashboard exposes existing public scalar health/availability information, not care notes.
- STAFF, CatController: POST `/api/cats`, POST `/api/cats/{id}/upload-image`, GET `/api/cats/{catId}/alerts`.
- STAFF, HealthDataController: GET and POST `/api/cats/{catId}/health`, GET `/api/cats/{catId}/health/alerts`.
- STAFF, AdoptionController: GET `/api/adoptions`, PATCH `/api/adoptions/{id}/approve`, PATCH `/api/adoptions/{id}/reject`.
- DEFER TO P4.3, still public, AdoptionController: POST `/api/adoptions` and GET `/api/adoptions/{id}`. Receipt ownership is unchanged.
- STAFF, AlertQueueController: GET `/api/alerts`, including both OPEN and CLOSED filters.
- STAFF, AlertResolutionController: PATCH `/api/alerts/{alertId}/resolve`.
- STAFF, CatHealthTimelineController: GET `/api/cats/{catId}/health-timeline`. This protects human care notes; no separate CareRecord CRUD API exists.
- PUBLIC identity initialization, AuthController: GET `/api/auth/csrf`. GET `/api/auth/me` is the session-dependent lifecycle exception: either authenticated role may read its own identity, anonymous receives 401.
- PUBLIC identity lifecycle, Spring Security filters: POST `/api/auth/login` and POST `/api/auth/logout`, both with mandatory CSRF. Logout is harmless if already anonymous.
- PUBLIC media: GET/HEAD `/uploads/**`, the existing image resource handler. No private stream or video proxy endpoint exists.
- PUBLIC management health: GET/HEAD `/actuator/health`, the sole exposed actuator endpoint. STAFF: other `/actuator/**` paths if enabled, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`.
- PUBLIC framework handling: static resources, `/error`, and CORS preflight under the existing origin policy. Unknown routes retain normal framework behavior.

## 2. Files changed

Paths below are relative to the repository. New files are marked **new**.

Backend production and dependencies:

- `backend/pom.xml` — test-scoped Spring Security test support.
- `backend/src/main/java/com/pawtrack/backend/identity/security/SessionSecurityConfiguration.java` — role rules and distinct denial messages.
- `backend/src/main/java/com/pawtrack/backend/adoption/api/AdoptionController.java` — Swagger description only.

Backend tests, under `backend/src/test/java/com/pawtrack/backend/`:

- **new** `identity/api/StaffAuthorizationIntegrationTest.java`.
- **new** `support/StaffMvcTestConfiguration.java` and `support/StaffRegression.java`.
- `identity/api/IdentitySessionIntegrationTest.java`.
- `adoption/api/AdoptionReviewIntegrationTest.java`.
- `alert/api/AlertQueueIntegrationTest.java`.
- `care/api/AlertResolutionIntegrationTest.java` and `care/api/CatHealthTimelineIntegrationTest.java`.
- `cat/api/CatControllerWebMvcTest.java`, `CatDashboardIntegrationTest.java`, `CatImageUploadIntegrationTest.java` and `TypedCatStatusIntegrationTest.java`.
- `healthdata/api/HealthAlertIntegrationTest.java` and `HealthObservationCorrectnessIntegrationTest.java`.

Frontend production, under `frontend/src/`:

- `App.tsx`, `api/axiosInstance.ts`, `api/types.ts`, `components/QueryState.tsx`, `hooks/queryClient.ts`, `pages/ApplicationPage.tsx` and `styles/globals.css`.
- **new** `api/auth.ts`, `components/AccountNavigation.tsx`, `components/RequireStaff.tsx`, `hooks/useCurrentUser.ts` and `pages/LoginPage.tsx`.

Browser tests:

- **new** `frontend/tests/care/auth-workspace.spec.ts`.
- `frontend/tests/care/care-workspace.spec.ts`, `frontend/tests/care/typed-cat-status.spec.ts` and `frontend/tests/live/milestone.spec.ts`.

Documentation:

- `README.md`, `backend/ARCHITECTURE.md`.
- **new** `docs/P4_2_ROUTE_INVENTORY.md` and this report.

## 3. Exact backend authorization rules

Rules are evaluated in this order:

1. `/api/auth/me`: authenticated.
2. GET and HEAD `/actuator/health`: permit all.
3. All methods on `/api/alerts`, `/api/alerts/*/resolve`, `/api/cats/*/health-timeline`, `/api/cats/*/health`, `/api/cats/*/health/alerts`, `/api/cats/*/alerts`, `/api/cats/*/upload-image`, `/api/adoptions/*/approve`, `/api/adoptions/*/reject`, `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`: `hasRole("STAFF")`, i.e. `ROLE_STAFF`.
4. GET and HEAD `/api/adoptions`: STAFF.
5. POST `/api/cats`: STAFF.
6. Remaining requests: permit all, subject to the existing CSRF policy and normal handler routing.

The shared cat/adoption collection paths use method-specific rules so public reads and submission remain available. Staff resource rules also protect HEAD. The fallback preserves existing framework/static routing; it is not a deny-by-default guarantee for future endpoints. Every new controller route needs explicit classification.

## 4. 401 and 403 behavior

- Anonymous staff API requests: JSON 401, `{"message":"Authentication required."}`.
- Authenticated ADOPTER staff requests: JSON 403, `{"message":"Staff access required."}`.
- Invalid credentials: JSON 401, `{"message":"Invalid email or password."}`, with no account-existence distinction.
- CSRF rejection: JSON 403, `{"message":"Request rejected. Refresh the CSRF token and retry."}`.
- API denial does not redirect to an HTML login form. Tests supply valid CSRF for ADOPTER writes, distinguishing role denial from earlier CSRF rejection.

## 5. CSRF integration design

P4.1's CookieCsrfTokenRepository and XOR submission-token contract remain intact. All unsafe auth requests and authenticated unsafe business requests require CSRF. Anonymous transitional business writes remain exempt; STAFF authorization still rejects anonymous management writes.

A small Axios interceptor initializes the submission token using GET `/api/auth/csrf` before any unsafe frontend request and attaches the returned header name/token. Tokens and an initialization promise live only in module memory; concurrent initialization is coalesced. Clearing state prevents an older initialization from repopulating the cache.

Login clears old state, initializes CSRF before the form POST, then obtains a fresh token after authentication. Reloaded sessions obtain a token before their next mutation. Logout submits valid CSRF and clears memory. A 403 clears the cached token for a subsequent explicit retry. Mutations are never automatically replayed. This also covers authenticated users submitting the still-public adoption form.

## 6. Frontend auth architecture

`authApi` provides csrf/login/me/logout operations; pages do not invoke Axios directly. CurrentUser and STAFF/ADOPTER types describe the safe identity DTO. React Query owns `current-user`, with a 15-second stale time and no authentication-error retries. An anonymous `/me` response becomes a null user; network failures remain visible errors.

The browser manages the existing session cookie. There is no localStorage/sessionStorage credential, session identifier or CSRF storage. Password input is cleared after either login outcome; the login mutation has zero garbage-collection delay after it becomes inactive. No JWT, refresh-token system or global credential store is added.

## 7. Login, logout and route guard

`/login` has labelled email/password inputs, native form submission, disabled/pending state and readable errors. Demo credentials are documented in README; the page does not add registration or expose credentials through a backend endpoint.

`RequireStaff` wraps `/staff` and `/staff/care`. Anonymous visitors go to login with router state. Only the existing `/staff` and `/staff/care` destinations are accepted; no external URL or query redirect is used. STAFF returns to the intended destination. ADOPTER sees “Staff access required” without a login loop and can continue public browsing.

The shell shows account name/role, STAFF workspace access and Sign out. Successful logout clears private/query and mutation state and returns home. A staff API 401 clears current-user and private query caches, causing the guard to require login rather than rendering an empty queue. Shared error UI also distinguishes sign-in-required and role denial. Reauthentication restores workspace use; unsent drafts are not persisted across that flow.

## 8. Staff workflow changes

ReviewPage and CarePage are unchanged. Existing approval/rejection, alert resolution, stale-response handling, draft behavior and 409 business errors remain under their original implementation. Add Cat and image-upload writes now require STAFF and receive CSRF through the shared client. The application receipt link was renamed from “Open demo staff review” to “Open staff review”. No new management feature was added.

## 9. Security tests added

`StaffAuthorizationIntegrationTest` adds 23 executed cases using real repository-backed form login, server sessions and the actual csrf endpoint:

- Three role cases exercise review queue, alert queue, timeline, health history, both alert aliases and HEAD review access.
- Eighteen role/operation cases cover approve, reject, resolve, Cat creation, health observation and image upload. Each STAFF case separately rejects missing CSRF before succeeding with a valid token. Each ADOPTER write has valid CSRF and receives the role-specific 403.
- One public compatibility case and one logout/documentation boundary case.

P4.1 session/fixation/CSRF tests remain, with its former permit-all business expectation updated. Business regression fixtures now provide STAFF and CSRF through Spring Security test support; filters are not disabled and domain assertions are preserved. The explicit role matrix does not use those mocked-principal fixtures.

## 10. Public compatibility tests

Anonymous tests verify gallery, detail, dashboard, health check, allowed application submission and receipt reads. An uploaded image remains publicly retrievable after a STAFF upload. Receipt assertions deliberately demonstrate the remaining applicant-email exposure by identifier. Browser tests preserve public typed-status behavior, and the real demo starts with anonymous browsing/submission and ends with ADOPTER public browsing.

## 11. Complete backend suite

`mvn clean test`: **119 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**. This includes all existing business/session/bootstrap regressions plus the new authorization matrix. Local log: `backend/p4-2-test.log`.

## 12. Frontend build, lint and browser results

- `npm run build`: passed TypeScript/Vite production build.
- `npm run lint`: passed.
- `npm run test:care`: **24 passed**, covering desktop and narrow projects, with no retries. The prior 16 cases remain; eight additional executions cover authentication, wrong credentials, ADOPTER denial, intended-route return, post-login token rotation, page reload, Add Cat CSRF, logout and session expiry/recovery.
- Existing mocked care fixtures now supply STAFF identity and CSRF; typed public fixtures supply anonymous identity. The framework and production care logic were not replaced.

Logs: `frontend/p4-2-build.log`, `p4-2-lint.log`, `p4-2-care.log`. Live narrow login and care screenshots were visually inspected; form controls and account navigation fit without horizontal overflow.

## 13. PostgreSQL migration and runtime results

All migration tests through V9 passed: **11 tests, 0 failures, 0 errors, 0 skipped**. Executed classes: PostgresMigrationIT, CareRecordPostgresMigrationIT, AlertResolutionPostgresMigrationIT, CatStatusPostgresMigrationIT, RemoveLegacyCatStatusPostgresMigrationIT and UserAccountPostgresMigrationIT.

`CatLockPostgresRuntimeIT`: **3 tests, 0 failures, 0 errors, 0 skipped**. The existing competing-approval, competing-resolution and health-write lock histories remain verified on PostgreSQL. These checks use disposable UUID schemas and clean them; the normal development schema was not migrated. Logs: `backend/p4-2-migrations.log` and `backend/p4-2-runtime.log`.

V1–V9, UserAccount schema, adoption schema, service transaction boundaries and Cat locking were not modified. These tests establish their particular transaction histories, not all interleavings or production throughput.

## 14. Real authenticated live demo

`npm run test:live`: **1 passed**, no retries, against the disposable real demo backend. There is no API interception or security bypass.

The flow browses anonymously, submits competing applications, requires login for review, logs in through the UI as demo STAFF, approves/rejects with real cookies/CSRF, resolves Nori's alert and checks existing adoption/care/typed-status outcomes. It exercises narrow and desktop care views, then logs out and verifies anonymous API 401. Demo ADOPTER login then produces the staff-denied UI, API 403 and a valid-CSRF write 403, while public browsing works.

Log: `frontend/p4-2-live.log`. Screenshot evidence is generated under `frontend/test-results/live/milestone-real-demo-comple-f8d87-on-and-care-vertical-slices/`, including `staff-login-narrow.png`, `care-open-narrow.png`, `care-closed-desktop.png` and the adopted-under-observation views. Logs and Playwright artifacts are local generated evidence, not source files.

## 15. Security guarantees now supported

Existing staff operations enforce the STAFF role on the backend, including care notes, aliases and management writes. Anonymous and ADOPTER requests cannot use those operations. The SPA reflects that boundary and supports session login/logout, CSRF-aware mutations and session-expiry recovery. The existing session fixation protection, password encoding and cookie policy remain in place.

This is a local, same-origin SPA/session implementation. It does not claim production deployment security, dynamic revocation of already-created principal snapshots or protection for unclassified future endpoints. Upload content hardening, pagination and the previously recorded domain freshness limitations are not resolved by this phase.

## 16. Remaining P4.3 privacy and ownership gap

POST `/api/adoptions` remains public, and GET `/api/adoptions/{id}` still returns an application receipt, including applicant information, without account ownership checks. Identifier knowledge is sufficient to read it. This is transitional and **not safe for public deployment or real applicant data**. No email/header surrogate ownership check was added.

Adopter ownership, registration, My applications, reviewedBy/resolvedBy attribution, password reset, email verification, MFA/OAuth and deployment security remain deferred. P4.2 is staff authorization plus authentication UI, not complete application authorization. Work stops here for review before P4.3.
