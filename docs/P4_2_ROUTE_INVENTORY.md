# P4.2 route inventory — recorded before security changes

Inspected every controller mapping, WebConfig media/CORS mappings, Spring Security auth handlers, actuator configuration and springdoc dependency. Classifications describe the intended P4.2 boundary, not P4.1's former permit-all state.

## CatController

- PUBLIC: GET `/api/cats` (gallery), GET `/api/cats/{id}` (basic detail), GET `/api/cats/{id}/dashboard` (public latest temperature and availability flag, no care notes).
- STAFF: POST `/api/cats` (existing Add Cat); POST `/api/cats/{id}/upload-image` (management upload); GET `/api/cats/{catId}/alerts` (staff alert detail).

## HealthDataController

- STAFF: POST `/api/cats/{catId}/health` (manual/internal observation input).
- STAFF: GET `/api/cats/{catId}/health` (observation history); GET `/api/cats/{catId}/health/alerts` (alternate alert listing).
- Public pages use dashboard scalar values, not either history route. Keep both alert aliases protected.

## AdoptionController

- DEFER TO P4.3, still public: POST `/api/adoptions` (submission); GET `/api/adoptions/{id}` (receipt by identifier, currently includes applicant information). No account owner or email-based substitute is added.
- STAFF: GET `/api/adoptions` (review queue); PATCH `/api/adoptions/{id}/approve`; PATCH `/api/adoptions/{id}/reject`.
- Do not protect the entire adoption namespace indiscriminately.

## Care and alerts

- STAFF: AlertQueueController GET `/api/alerts`, for OPEN and CLOSED filters alike.
- STAFF: AlertResolutionController PATCH `/api/alerts/{alertId}/resolve`.
- STAFF: CatHealthTimelineController GET `/api/cats/{catId}/health-timeline`, including human care notes. No other CareRecord CRUD routes exist.

## AuthController and Spring Security handlers

- PUBLIC initialization: GET `/api/auth/csrf`.
- PUBLIC login attempt with mandatory CSRF: POST `/api/auth/login` (form filter).
- Session-dependent, both roles: GET `/api/auth/me` (200 authenticated / 401 anonymous).
- PUBLIC logout operation with mandatory CSRF: POST `/api/auth/logout` (logout filter, harmless if already anonymous).

These lifecycle routes are neither STAFF-only nor deferred identity work; the identity itself was completed in P4.1.

## Media, management and framework routes

- PUBLIC: GET/HEAD `/uploads/**`, mapped by WebConfig to the existing upload directory, needed for public Cat portraits. It serves images, not care records. No stream proxy/private-video endpoint exists.
- PUBLIC: GET/HEAD `/actuator/health`, the sole currently exposed actuator endpoint, needed by the existing disposable live runner. Other actuator paths are STAFF if enabled later.
- STAFF: `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` (developer/management documentation; no public page requires them).
- PUBLIC framework routing: static SPA resources, CORS preflights under the existing origin policy, and `/error`. Unknown routes retain normal framework not-found behavior; no new business route is added.

Read restrictions also cover HEAD and other methods on staff-only resource paths. The shared `/api/adoptions` and `/api/cats` collection paths use method-specific matchers to preserve submission and browsing. No migration is required.
