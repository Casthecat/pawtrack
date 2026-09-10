# P4 final route inventory

Re-inspected all eight controllers, WebConfig, auth filters, actuator and springdoc configuration after P4.3, before P4.4 security changes. GET classifications also allow HEAD. Numeric entity path variables are explicitly constrained in the security allowlist so a future literal endpoint under `/api/cats` does not inherit public access.

## PUBLIC

- CatController: GET `/api/cats`, `/api/cats/{id}`, `/api/cats/{id}/dashboard`.
- WebConfig: GET `/uploads/**` for public portraits. There is no private media/stream proxy API.
- Actuator: GET `/actuator/health`, the sole exposed actuator endpoint.
- AuthController: GET `/api/auth/csrf`.
- Spring Security lifecycle filters: POST `/api/auth/login` and `/api/auth/logout`, with mandatory CSRF. Login authenticates credentials; logout invalidates any current session.
- Non-API SPA/static routes remain publicly servable. CORS preflight is handled by the existing origin policy; it does not authorize the corresponding business request.

## AUTHENTICATED

- AuthController: GET `/api/auth/me`.
- AdoptionController: GET `/api/adoptions/{id}`. Service then permits STAFF or the owning ADOPTER; other adopters, including matching-email historical NULL-owner rows, receive normal 404.

## ROLE_ADOPTER

- AdoptionController: POST `/api/adoptions`.
- MyAdoptionsController: GET `/api/me/adoptions`.

## ROLE_STAFF

- AdoptionController: GET `/api/adoptions`, PATCH `/api/adoptions/{id}/approve`, PATCH `/api/adoptions/{id}/reject`.
- AlertQueueController: GET `/api/alerts` for both OPEN and CLOSED filters.
- AlertResolutionController: PATCH `/api/alerts/{id}/resolve`.
- CatHealthTimelineController: GET `/api/cats/{id}/health-timeline`, including care notes.
- HealthDataController: GET and POST `/api/cats/{id}/health`; GET `/api/cats/{id}/health/alerts`.
- CatController: GET `/api/cats/{id}/alerts` (the other alert alias), POST `/api/cats`, POST `/api/cats/{id}/upload-image`.
- Developer documentation: `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`. Remaining actuator paths require STAFF if enabled later.

## DENIED

After the explicit API method/path allowlist: `/api` and `/api/**` are denyAll for every role, including STAFF. Unsupported methods and removed endpoints do not inherit a role's broad namespace access. Protected anonymous requests return JSON 401; authenticated denial returns JSON 403. Retired `/api/cats/{id}/status` and `/api/alerts/{id}/close` consequently return 403 to STAFF instead of its former handler-level 404. No production probe route is added.
