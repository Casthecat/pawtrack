package com.pawtrack.backend.identity.api;

import com.pawtrack.backend.support.TestAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.adoption.service.AdoptionService;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import com.pawtrack.backend.healthdata.service.HealthDataService;
import com.pawtrack.backend.identity.domain.UserRole;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:staff-authorization;DB_CLOSE_DELAY=-1", "upload.path=target/authz-uploads/"})
@ActiveProfiles("test") @AutoConfigureMockMvc @DirtiesContext
class StaffAuthorizationIntegrationTest {
    static {
        // Resource locations are resolved when the application context starts.
        try { Files.createDirectories(Path.of("target/authz-uploads")); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired IdentityService identity;
    @Autowired UserAccountRepository accounts;
    @Autowired CatRepository cats;
    @Autowired AdoptionApplicationRepository applications;
    @Autowired AdoptionService adoption;
    @Autowired HealthDataService health;
    @Autowired HealthDataRepository observations;
    @Autowired AlertRepository alerts;
    @Autowired CareRecordRepository care;
    private Long catId, careCatId, applicationId, alertId;
    private Path uploadedFile;

    @BeforeEach void fixtures() {
        care.deleteAllInBatch(); applications.deleteAllInBatch(); alerts.deleteAllInBatch();
        observations.deleteAllInBatch(); cats.deleteAllInBatch(); accounts.deleteAllInBatch();
        identity.createAccount("staff@example.com", "Staff", "Test-password!", UserRole.STAFF);
        identity.createAccount("adopter@example.com", "Adopter", "Test-password!", UserRole.ADOPTER);
        catId = cats.saveAndFlush(new Cat("Public cat")).getId();
        careCatId = cats.saveAndFlush(new Cat("Care cat")).getId();
        health.create(careCatId, null, new BigDecimal("40.00"), 1);
        alertId = alerts.findAll().getFirst().getId();
        var request = new AdoptionApplicationRequest();
        request.setCatId(catId);
        applicationId = adoption.submitApplication(request, TestAccounts.adopter(accounts, "adopter@example.com")).id();
    }
    @AfterEach void removeUploadedFixture() throws Exception { if (uploadedFile != null) Files.deleteIfExists(uploadedFile); }

    @ParameterizedTest @ValueSource(strings = {"ANONYMOUS", "ADOPTER", "STAFF"})
    void staffReadsHaveTheCorrectRoleBoundaryIncludingAliases(String role) throws Exception {
        var session = session(role);
        int expected = role.equals("ANONYMOUS") ? 401 : role.equals("ADOPTER") ? 403 : 200;
        for (String path : new String[]{"/api/adoptions", "/api/alerts", "/api/cats/" + careCatId + "/health-timeline",
                "/api/cats/" + careCatId + "/health", "/api/cats/" + careCatId + "/alerts",
                "/api/cats/" + careCatId + "/health/alerts"}) {
            var request = get(path);
            if (session != null) request.session(session);
            var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
            assertNull(result.getResponse().getRedirectedUrl());
            if (expected != 200) assertTrue(json.readTree(result.getResponse().getContentAsString()).has("message"));
        }
        var headRequest = head("/api/adoptions");
        if (session != null) headRequest.session(session);
        mvc.perform(headRequest).andExpect(status().is(expected));
    }

    static Stream<Arguments> mutations() {
        return Stream.of("ANONYMOUS", "ADOPTER", "STAFF").flatMap(role ->
                Stream.of("approve", "reject", "resolve", "create", "health", "upload").map(operation -> Arguments.of(role, operation)));
    }

    @ParameterizedTest @MethodSource("mutations")
    void managementWritesRequireStaffAndStaffMustSupplyCsrf(String role, String operation) throws Exception {
        var session = session(role);
        if (role.equals("STAFF")) {
            mvc.perform(request(operation).session(session)).andExpect(status().isForbidden());
        }
        var request = request(operation);
        if (session != null) authorize(request, session); // Real token, so ADOPTER denial proves the role check.
        int expected = role.equals("ANONYMOUS") ? 401 : role.equals("ADOPTER") ? 403 : 200;
        var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        assertNull(result.getResponse().getRedirectedUrl());
        if (role.equals("ADOPTER")) assertEquals("Staff access required.", json.readTree(result.getResponse().getContentAsString()).get("message").asText());
        if (operation.equals("upload") && expected == 200) uploadedFile = Path.of("target/authz-uploads").resolve(Path.of(json.readTree(result.getResponse().getContentAsString()).get("imageUrl").asText()).getFileName());
        if (expected != 200) {
            assertEquals(2, cats.count());
            assertEquals(0, care.count());
            assertEquals("PENDING", applications.findById(applicationId).orElseThrow().getStatus().name());
        }
    }

    @Test void anonymousPublicReadsAndMediaRemainAvailableButApplicationsRequireLogin() throws Exception {
        for (String path : new String[]{"/api/cats", "/api/cats/" + catId, "/api/cats/" + catId + "/dashboard", "/actuator/health"})
            mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(post("/api/adoptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"catId\":" + catId + "}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/adoptions/" + applicationId)).andExpect(status().isUnauthorized());
        var upload = mvc.perform(authorize(request("upload"), session("STAFF"))).andExpect(status().isOk()).andReturn();
        String path = json.readTree(upload.getResponse().getContentAsString()).get("imageUrl").asText();
        uploadedFile = Path.of("target/authz-uploads").resolve(Path.of(path).getFileName());
        mvc.perform(get("/uploads/" + uploadedFile.getFileName())).andExpect(status().isOk());
    }

    @Test void logoutRemovesStaffAccessAndDocsAreNotPublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        var session = session("STAFF");
        mvc.perform(get("/api/adoptions").session(session)).andExpect(status().isOk());
        mvc.perform(authorize(post("/api/auth/logout"), session)).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/adoptions")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "approve", "reject" -> patch("/api/adoptions/" + applicationId + "/" + operation);
            case "resolve" -> patch("/api/alerts/" + alertId + "/resolve").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"careType\":\"CHECKUP\",\"note\":\"Staff checked the cat.\"}");
            case "create" -> post("/api/cats").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New cat\"}");
            case "health" -> post("/api/cats/" + catId + "/health").contentType(MediaType.APPLICATION_JSON).content("{\"temperatureC\":38.50,\"activityLevel\":2}");
            case "upload" -> multipart("/api/cats/" + catId + "/upload-image").file(new MockMultipartFile("file", "test.jpg", "image/jpeg", com.pawtrack.backend.support.TestImages.image("jpeg")));
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private MockHttpSession session(String role) throws Exception {
        if (role.equals("ANONYMOUS")) return null;
        var login = post("/api/auth/login").param("email", role.toLowerCase(java.util.Locale.ROOT) + "@example.com").param("password", "Test-password!");
        var result = mvc.perform(authorize(login, null)).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        var init = get("/api/auth/csrf");
        if (session != null) { init.session(session); request.session(session); }
        var response = mvc.perform(init).andExpect(status().isOk()).andReturn().getResponse();
        var token = json.readTree(response.getContentAsString());
        return request.cookie(response.getCookie("XSRF-TOKEN")).header(token.get("headerName").asText(), token.get("token").asText());
    }
}
