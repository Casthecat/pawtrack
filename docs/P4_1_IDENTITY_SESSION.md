# P4.1 — Identity and Session Authentication Foundation

Completed on 2026-09-10 on `feat/p4-auth`, created from the clean completed P3 branch because the requested P4 branch did not yet exist locally. P1/P2 remains frozen at `v1.0-portfolio-core`. P4.1 provides authentication infrastructure only. Business authorization and application ownership are not implemented.

## 1. Files changed

Production additions under `backend/src/main/java/com/pawtrack/backend/identity/`:

- `domain/UserAccount.java`, `domain/UserRole.java`.
- `repo/UserAccountRepository.java`, `service/IdentityService.java`.
- `security/AccountPrincipal.java`, `security/AccountDetailsService.java`, `security/PasswordConfiguration.java`, `security/SessionSecurityConfiguration.java`.
- `api/AuthController.java`, `api/dto/CurrentUserResponse.java`.
- `config/DemoAccounts.java`.

Other production/config changes: added `backend/src/main/resources/db/migration/V9__create_user_accounts.sql`; updated `backend/pom.xml` and `backend/src/main/resources/application.yml`.

Added tests under `backend/src/test/java/com/pawtrack/backend/`:

- `identity/api/IdentitySessionIntegrationTest.java`.
- `identity/api/DemoAccountsIntegrationTest.java`.
- `identity/api/UserAccountPostgresMigrationIT.java`.
- `support/PreIdentityMigrationApplication.java`.

Updated tests in that test root:

- `cat/api/CatControllerWebMvcTest.java`, `cat/api/CatDashboardIntegrationTest.java`, `healthdata/api/HealthAlertIntegrationTest.java`: import the actual transitional security configuration into isolated test contexts instead of Spring Boot's default all-protected configuration. MVC-only tests mock the identity lookup, not the filters.
- `cat/api/CatStatusPostgresMigrationIT.java`, `cat/api/RemoveLegacyCatStatusPostgresMigrationIT.java`: retain their historical targets and assertions; validate only the five pre-identity domain modules via the shared historical test configuration. They do not incorrectly require a V9 table on V7/V8.
- `cat/api/HistoricalMigrationIntegrityTest.java`: extend the unchanged-content guard through V8.

Documentation: updated `backend/ARCHITECTURE.md` and added this report. Frontend source/tests/config, V1–V8, Cat domain, adoption/care logic, six-cat seeds and prior milestone records remain unchanged.

## 2. Identity schema and domain

V9 creates `user_accounts`: generated bigint primary key; email varchar(200); display_name varchar(120); password_hash varchar(255); role varchar(20); created_at/updated_at timestamptz. All columns are non-null. Email has a named unique constraint and a trim/lowercase storage CHECK; role CHECK permits only STAFF and ADOPTER. No ownership or staff-action foreign keys are added.

UserAccount uses enum string persistence and JPA validation for email, nonblank fields, maximum lengths and required role. Role must be supplied explicitly; it never defaults to STAFF. Persistence initializes both timestamps and updates updatedAt on entity update. No account editing API is introduced. The internal service is transactional and returns an entity only to trusted internal callers; auth controllers return explicit DTOs.

## 3. Email normalization

Both internal account creation and UserDetails lookup use `trim().toLowerCase(Locale.ROOT)`. The stored normalized email is also the Spring Security username. Creation rejects malformed/empty email and duplicate normalized identity through database uniqueness, including concurrent insert protection. There is no separate username. Tests include mixed case, surrounding whitespace and a Turkish default locale to verify Locale.ROOT behavior.

V9's normalization CHECK additionally rejects non-normalized direct SQL examples. It is not a claim that every internationalized-email equivalence or arbitrary external provisioning policy has been solved.

## 4. Password storage

PasswordEncoderFactories creates the standard DelegatingPasswordEncoder; current hashes have the `{bcrypt}` prefix and use Spring Security BCrypt. No custom cryptography, salts or plaintext persistence is implemented. The internal creation path rejects blank passwords and input beyond BCrypt's 72 UTF-8-byte limit. Public password policies/onboarding are deferred because registration does not exist.

Auth DTOs contain no password or hash; tests assert the exact four returned fields. Application code never logs submitted passwords. AccountPrincipal extends Spring Security User, whose credentials are erased after successful authentication; tests verify the session principal's password is null. The repository stores only the encoded hash. See Spring's [password storage documentation](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) for the standard encoder model.

## 5. Spring Security and session design

The Spring Boot-managed Security dependency is added without upgrading the existing Boot stack. Standard UsernamePasswordAuthenticationFilter/form login delegates to repository-backed AccountDetailsService through Spring Security's authentication machinery. AccountPrincipal is a serializable detached identity snapshot with account ID, normalized username/email, display name and role authority. A JPA entity is never the session principal.

Framework SecurityContext persistence, changeSessionId fixation protection and logout handlers manage the session. No arbitrary objects are manually placed in a session by production code. Request caching is disabled so API authentication does not redirect to a saved business request. Success/failure handlers return JSON; unauthenticated me returns 401.

This single SPA and modular monolith use one server-side session lifecycle; JWT/access-refresh token infrastructure adds no current product value. No Redis/Spring Session, remember-me token or localStorage credential storage is introduced. The existing [Spring form-login mechanism](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html) supplies authentication/session integration.

## 6. CSRF design and SPA flow

CookieCsrfTokenRepository stores the expected token in `XSRF-TOKEN`, path `/`, HttpOnly, SameSite=Lax. The framework's default XOR request handler remains enabled. `GET /api/auth/csrf` materializes the deferred token and returns `{ "headerName": "X-XSRF-TOKEN", "token": "..." }`. The response token is the XOR-encoded submission value, not an authentication credential.

Future same-origin SPA callers should:

1. GET `/api/auth/csrf`, retaining cookies and the returned token in memory.
2. POST form-encoded email/password to `/api/auth/login`, sending the returned token in the named header and retaining cookies.
3. After successful login, GET `/api/auth/csrf` again because the framework clears the old CSRF cookie on authentication success.
4. Include the header on subsequent session-authenticated writes, including POST logout. After logout, initialize a fresh token before another login.

JavaScript does not need to read either HttpOnly cookie. Do not copy the raw cookie into a header when using this response-token flow. Same-origin Vite proxy requests are the supported SPA topology; credentialed direct cross-origin integration is not enabled or proven here. Current frontend source is unchanged and has no login integration; an API caller that authenticates must follow this CSRF contract for later writes.

Protection applies to every unsafe method under `/api/auth/**`, including anonymous login/logout, and to every unsafe request carrying authenticated SecurityContext state. Safe methods retain the framework definition GET/HEAD/TRACE/OPTIONS. Anonymous business writes are the explicit transitional exemption; CSRF is not globally disabled. Tests use real cookie/token exchange, not a CSRF test shortcut. Background reference: Spring's [CSRF documentation](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## 7. Auth contracts

- `GET /api/auth/csrf`: public 200 with headerName/token and a CSRF cookie. No account data.
- `POST /api/auth/login`: `application/x-www-form-urlencoded`, fields `email` and `password`, valid CSRF cookie/header pair required. Success 200 returns `{id,email,displayName,role}` and establishes or rotates the server session. Wrong password and unknown email both return 401 with `{ "message": "Invalid email or password." }`. Missing/invalid CSRF returns 403 before authentication.
- `GET /api/auth/me`: authenticated 200 with the same four-field DTO; unauthenticated 401 with `{ "message": "Authentication required." }`.
- `POST /api/auth/logout`: valid CSRF required; normal Spring logout invalidates the HTTP session, clears authentication/cookies and returns 204. Following me returns 401. Missing CSRF returns 403 and preserves the existing authenticated session.

There is no registration, auth UI, generic role mutation, JWT endpoint or handwritten login controller. GET login is not a generated HTML login page.

## 8. Demo accounts

Only `demo` profile runs DemoAccounts. The disposable H2 database receives exactly these fake identities on fresh startup:

- STAFF: `staff@example.com`, display name Demo Staff, password `PawTrack-demo-staff!`.
- ADOPTER: `adopter@example.com`, display name Demo Adopter, password `PawTrack-demo-adopter!`.

These are documented **DEMO-ONLY** credentials, stored encoded like other passwords. They are not real accounts and must not be used outside the disposable local demo. Restarting a fresh demo rebuilds both fixtures; existing matching fixture emails are not overwritten. Default/test/PostgreSQL validation profiles do not provision them. Six cats and the Nori/Mochi workflow are unchanged.

## 9. Exact transitional permit-all rules

`/api/auth/me` requires authentication. Every other path has authorization `permitAll`. Therefore adoption submission, receipts, review, care queue and alert resolution remain reachable without login or role/ownership checks. Spring Security still applies its CSRF, CORS and normal filter behavior; authenticated unsafe requests need a token even on permit-all routes.

No hasRole rule or method authorization was added. Possession of STAFF or ADOPTER authorities does not yet limit business actions. P4.1 must not be described as completed authorization. P4.2/P4.3 will replace these transitional rules.

## 10. Tests added

IdentitySessionIntegrationTest executes nine cases: two role-specific normalization/encoding/duplicate tests; required-field validation; two role-specific login/session-rotation/principal/DTO tests; equivalent bad credentials and anonymous me; real CSRF rejection/success/logout; anonymous business compatibility; and a real HTTP server round trip verifying JSESSIONID HttpOnly/SameSite=Lax, persisted me, token renewal and logout.

DemoAccountsIntegrationTest verifies exactly two demo accounts, correct roles and encoded known-password matches. The non-demo identity test and V9 PostgreSQL context verify no automatic fixture accounts. UserAccountPostgresMigrationIT covers V8 → V9, all seven column types/lengths/nullability, normalized duplicate rejection, both roles, role/email/null constraints, unchanged adopted+SICK Cat data, Hibernate validate, fresh account persistence and no-op rerun. Existing Cat-lock tests retain their assertions and now run against V9.

## 11. Complete backend result

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' clean test
```

**96 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** This includes all 86 P3.2 default cases plus ten new identity/demo cases. Initial test configuration failures were fixed by importing the real security configuration into isolated legacy test contexts; no business assertions or filters were disabled. Local ignored log: `backend/p4-1-test.log`.

## 12. Frontend regression results

Frontend source, tests and configuration are unchanged, confirmed by Git diff.

- `npm run build`: passed.
- `npm run lint`: passed.
- `npm run test:care`: **16 passed**, desktop and narrow viewports, no retries.
- `npm run test:live`: **1 passed**, no retries, both real adoption and care slices plus P3's adopted/under-observation presentation.

The existing live runner starts its own fresh H2 demo process on 19091 and Vite on 5173, with no API mocks or reuse. No login was added to that workflow, proving its anonymous compatibility. Logs are `frontend/p4-1-build.log`, `p4-1-lint.log`, `p4-1-care.log`, `p4-1-live.log` (ignored).

## 13. PostgreSQL migration result

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' '-Dtest=PostgresMigrationIT,CareRecordPostgresMigrationIT,AlertResolutionPostgresMigrationIT,CatStatusPostgresMigrationIT,RemoveLegacyCatStatusPostgresMigrationIT,UserAccountPostgresMigrationIT' test
```

**11 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**, local PostgreSQL 16.11 with PAWTRACK_TEST_JDBC_URL/DB_USER/DB_PASSWORD configured. Migration writes use disposable UUID schemas cleaned in finally; the real development public schema was not migrated. Historical V7/V8 tests retain their versions and data checks with pre-identity JPA scanning; V9 validates the complete current application. Log: `backend/p4-1-migrations.log`.

## 14. PostgreSQL runtime result

```powershell
mvn.cmd -o '-Dmaven.repo.local=C:/Users/77369/.m2/repository' '-Dtest=CatLockPostgresRuntimeIT' test
```

**3 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** Competing approvals, competing resolutions and resolution-before-waiting-health-write behavior remain correct under real PostgreSQL locks and Hibernate validate. Log: `backend/p4-1-runtime.log`. Java 21 and the existing Maven cache were used for backend and live tests.

## 15. Security guarantees actually verified

Supported provisioning normalizes and uniquely stores identities with encoded passwords. Auth responses expose only the intended DTO fields. Both roles authenticate through the real repository; unknown-user and wrong-password responses are identical. Authentication establishes/persists a session, rotates a pre-existing session ID and erases the principal's password. Real HTTP cookies carry HttpOnly/SameSite=Lax. Missing/invalid CSRF is rejected on tested auth writes and authenticated business writes; valid token flow succeeds. Logout invalidates session authentication. Non-demo profiles do not create known-password fixtures.

## 16. Guarantees not proven or not implemented

Business authorization, receipt ownership and protection of applicant information are explicitly absent. Anonymous writes remain allowed. These tests are not an authorization/security audit, penetration test, timing side-channel proof, brute-force/rate-limit defense or load test. They do not prove TLS, Secure-cookie deployment, reverse-proxy trust, cross-origin credential handling, XSS prevention, distributed sessions or production readiness.

Session configuration uses cookie-only tracking and a 30-minute idle timeout; the test does not wait 30 minutes to verify expiration. Local HTTP cookies are not claimed to be Secure. HTTPS deployment must configure Secure session cookies and trusted proxy behavior appropriately; CSRF-cookie secure behavior must also be checked in that environment. No deployment configuration was added.

## 17. Deferred P4 work and assumptions

P4.2/P4.3 will implement staff endpoint authorization, adopter ownership/onboarding and frontend auth/CSRF integration. There is no registration, my-applications API, password reset, email verification, MFA, OAuth or staff attribution yet. Principal display name and role are login-time snapshots; future account/role editing must define session invalidation or refresh policy. No active account-management API exists in P4.1.

Session state is single-process and ends on restart; no persistence/cluster requirement is claimed. Demo-only known passwords are intentionally public fixtures, not production provisioning. The existing CORS allowlist is unchanged. V1–V8 are unchanged and guarded by the content test. Cat status and adoption/care transactions were not refactored. Changes remain uncommitted for review. Stop after P4.1.
