package com.pawtrack.backend.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.identity.domain.*;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.security.AccountPrincipal;
import com.pawtrack.backend.identity.service.IdentityService;
import jakarta.servlet.http.Cookie;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.net.*;
import java.net.http.*;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:identity-session;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @AutoConfigureMockMvc @DirtiesContext
class IdentitySessionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityService identity;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    private static final String PASSWORD = "Only-a-test-password!";

    @BeforeEach void reset() { accounts.deleteAllInBatch(); }

    @ParameterizedTest @EnumSource(UserRole.class)
    void supportedCreationNormalizesStoresEncodedPasswordAndRole(UserRole role) {
        Locale previous = Locale.getDefault();
        UserAccount saved;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            saved = identity.createAccount("  IDENTITY@EXAMPLE.COM  ", " Demo Identity ", PASSWORD, role);
        } finally { Locale.setDefault(previous); }
        var stored = accounts.findById(saved.getId()).orElseThrow();
        assertEquals("identity@example.com", stored.getEmail());
        assertEquals("Demo Identity", stored.getDisplayName());
        assertEquals(role, stored.getRole());
        assertNotNull(stored.getCreatedAt());
        assertEquals(stored.getCreatedAt(), stored.getUpdatedAt());
        assertTrue(stored.getPasswordHash().startsWith("{bcrypt}"));
        assertNotEquals(PASSWORD, stored.getPasswordHash());
        assertTrue(passwords.matches(PASSWORD, stored.getPasswordHash()));
        assertThrows(DataIntegrityViolationException.class,
                () -> identity.createAccount(" Identity@example.com ", "Duplicate", PASSWORD, role));
        assertEquals(1, accounts.count());
    }

    @Test void rejectsMissingOrInvalidIdentityFields() {
        assertThrows(ConstraintViolationException.class, () -> identity.createAccount("", "Name", PASSWORD, UserRole.ADOPTER));
        assertThrows(ConstraintViolationException.class, () -> identity.createAccount("not-an-email", "Name", PASSWORD, UserRole.ADOPTER));
        assertThrows(ConstraintViolationException.class, () -> identity.createAccount("a@example.com", " ", PASSWORD, UserRole.ADOPTER));
        assertThrows(ConstraintViolationException.class, () -> identity.createAccount("a@example.com", "Name", PASSWORD, null));
        assertThrows(IllegalArgumentException.class, () -> identity.createAccount("a@example.com", "Name", "", UserRole.ADOPTER));
        assertThrows(IllegalArgumentException.class, () -> identity.createAccount("a@example.com", "Name", "x".repeat(73), UserRole.ADOPTER));
        assertEquals(0, accounts.count());
    }

    @ParameterizedTest @EnumSource(UserRole.class)
    void loginRotatesSessionPersistsDetachedPrincipalAndReturnsSafeMe(UserRole role) throws Exception {
        var account = identity.createAccount("person@example.com", "Demo Person", PASSWORD, role);
        var oldSession = new MockHttpSession();
        var oldId = oldSession.getId();
        var login = login(" PERSON@EXAMPLE.COM ", PASSWORD, oldSession).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        assertNotEquals(oldId, session.getId());
        assertSafeUser(login.getResponse().getContentAsString(), account);
        var me = mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andReturn();
        assertSafeUser(me.getResponse().getContentAsString(), account);
        var context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        var principal = (AccountPrincipal) context.getAuthentication().getPrincipal();
        assertEquals(account.getId(), principal.getAccountId());
        assertNull(principal.getPassword());
        assertEquals("person@example.com", principal.getUsername());
        assertEquals("ROLE_" + role.name(), principal.getAuthorities().iterator().next().getAuthority());
    }

    @Test void unauthenticatedMeAndEquivalentBadCredentialsReturn401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
        identity.createAccount("person@example.com", "Person", PASSWORD, UserRole.STAFF);
        String wrong = login("person@example.com", "wrong", null).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = login("unknown@example.com", "wrong", null).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertEquals(wrong, unknown);
        assertEquals("Invalid email or password.", json.readTree(wrong).get("message").asText());
        assertFalse(wrong.contains("passwordHash"));
    }

    @Test void csrfRequiredForLoginLogoutAndAuthenticatedBusinessWrites() throws Exception {
        identity.createAccount("person@example.com", "Person", PASSWORD, UserRole.STAFF);
        mvc.perform(post("/api/auth/login").param("email", "person@example.com").param("password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
        var token = csrf(null);
        mvc.perform(post("/api/auth/login").cookie(token.cookie()).header(token.header(), "incorrect")
                .param("email", "person@example.com").param("password", PASSWORD)).andExpect(status().isForbidden());
        var session = (MockHttpSession) login("person@example.com", PASSWORD, null)
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(post("/api/cats").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Protected\"}"))
                .andExpect(status().isForbidden());
        var fresh = csrf(session);
        mvc.perform(post("/api/cats").session(session).cookie(fresh.cookie()).header(fresh.header(), fresh.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Protected\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).cookie(fresh.cookie()).header(fresh.header(), fresh.token()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void anonymousPublicReadsRemainAccessibleButStaffOperationsRequireLogin() throws Exception {
        assertEquals(0, accounts.count()); // No demo provisioning in the test/default profile.
        mvc.perform(get("/api/adoptions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cats").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Anonymous demo\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cats")).andExpect(status().isOk());
    }

    @Test void realCookieSessionIsHttpOnlySameSiteAndEndsAfterLogout() throws Exception {
        identity.createAccount("browser@example.com", "Browser Person", PASSWORD, UserRole.ADOPTER);
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        URI base = URI.create("http://127.0.0.1:" + port);
        var init = client.send(HttpRequest.newBuilder(base.resolve("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, init.statusCode());
        var token = json.readTree(init.body());
        var loggedIn = client.send(HttpRequest.newBuilder(base.resolve("/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(token.get("headerName").asText(), token.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString("email=browser%40example.com&password=" + URLEncoder.encode(PASSWORD, java.nio.charset.StandardCharsets.UTF_8)))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loggedIn.statusCode(), loggedIn.body());
        String sessionCookie = loggedIn.headers().allValues("set-cookie").stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertTrue(sessionCookie.contains("HttpOnly"));
        assertTrue(sessionCookie.contains("SameSite=Lax"));
        assertEquals(200, client.send(HttpRequest.newBuilder(base.resolve("/api/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        var renewed = client.send(HttpRequest.newBuilder(base.resolve("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        token = json.readTree(renewed.body());
        var logout = client.send(HttpRequest.newBuilder(base.resolve("/api/auth/logout"))
                .header(token.get("headerName").asText(), token.get("token").asText())
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, logout.statusCode());
        assertEquals(401, client.send(HttpRequest.newBuilder(base.resolve("/api/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private ResultActions login(String email, String password, MockHttpSession session) throws Exception {
        var token = csrf(session);
        var request = post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(token.cookie()).header(token.header(), token.token()).param("email", email).param("password", password);
        if (session != null) request.session(session);
        return mvc.perform(request);
    }

    private Token csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        return new Token(cookie, body.get("headerName").asText(), body.get("token").asText());
    }
    private record Token(Cookie cookie, String header, String token) {}

    private void assertSafeUser(String body, UserAccount account) throws Exception {
        var data = json.readTree(body);
        var fields = new java.util.HashSet<String>();
        data.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "email", "displayName", "role"), fields);
        assertEquals(account.getId().longValue(), data.get("id").asLong());
        assertEquals(account.getEmail(), data.get("email").asText());
        assertEquals(account.getDisplayName(), data.get("displayName").asText());
        assertEquals(account.getRole().name(), data.get("role").asText());
        assertFalse(body.contains(PASSWORD));
        assertFalse(body.contains(account.getPasswordHash()));
    }
}
