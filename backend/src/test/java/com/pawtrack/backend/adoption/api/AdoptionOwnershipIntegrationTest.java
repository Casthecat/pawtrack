package com.pawtrack.backend.adoption.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.domain.AdoptionApplication;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.adoption.service.AdoptionService;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.identity.domain.UserRole;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import com.pawtrack.backend.support.TestAccounts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ownership;DB_CLOSE_DELAY=-1", "spring.jpa.open-in-view=false"})
@ActiveProfiles("test") @AutoConfigureMockMvc @DirtiesContext
class AdoptionOwnershipIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired IdentityService identity;
    @Autowired UserAccountRepository accounts;
    @Autowired CatRepository cats;
    @Autowired AdoptionApplicationRepository applications;
    @Autowired AdoptionService adoptions;
    @Autowired JdbcTemplate jdbc;
    private Long catId, ownerId;

    @BeforeEach void setup() {
        applications.deleteAllInBatch(); cats.deleteAllInBatch(); accounts.deleteAllInBatch();
        ownerId = identity.createAccount(" OWNER@EXAMPLE.COM ", "Owner Name", "Test-password!", UserRole.ADOPTER).getId();
        identity.createAccount("other@example.com", "Other Name", "Test-password!", UserRole.ADOPTER);
        identity.createAccount("staff@example.com", "Staff", "Test-password!", UserRole.STAFF);
        catId = cats.saveAndFlush(new Cat("Companion")).getId();
    }

    @Test void submissionRequiresAdopterAndValidCsrf() throws Exception {
        mvc.perform(input(catId)).andExpect(status().isUnauthorized());
        mvc.perform(csrf(input(catId), login("staff"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Adopter access required."));
        var owner = login("owner");
        mvc.perform(input(catId).session(owner)).andExpect(status().isForbidden());
        assertEquals(0, applications.count());
        submit(owner, catId);
        assertEquals(1, applications.count());
    }

    @Test void bodyIdentityCannotOverrideAccountAndResponseHasNoAccountSecrets() throws Exception {
        var result = mvc.perform(csrf(post("/api/adoptions").contentType(MediaType.APPLICATION_JSON).content("""
                {"catId":%d,"notes":"  Quiet home  ","adopterName":"Impersonated","adopterEmail":"other@example.com","adopterAccountId":999}
                """.formatted(catId)), login("owner")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.adopterName").value("Owner Name"))
                .andExpect(jsonPath("$.adopterEmail").value("owner@example.com"))
                .andExpect(jsonPath("$.notes").value("Quiet home"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.adopterAccount").doesNotExist()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(ownerId, applications.findById(id).orElseThrow().getAdopterAccount().getId());
    }

    @Test void duplicatesUseAccountEvenIfSnapshotEmailChangesAndTerminalDecisionAllowsReapplication() throws Exception {
        var owner = login("owner");
        long id = submit(owner, catId);
        jdbc.update("update adoption_applications set adopter_email=? where id=?", "old-snapshot@example.com", id);
        mvc.perform(csrf(input(catId), owner)).andExpect(status().isConflict());
        submit(login("other"), catId);
        assertEquals(2, applications.count());
        adoptions.rejectApplication(id);
        submit(owner, catId);
        assertEquals(3, applications.count());
    }

    @Test void receiptAllowsOwnerAndStaffButHidesOtherAccountsAndAnonymous() throws Exception {
        var owner = login("owner");
        long id = submit(owner, catId);
        mvc.perform(get("/api/adoptions/" + id).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.adopterEmail").value("owner@example.com"));
        mvc.perform(get("/api/adoptions/" + id).session(login("staff"))).andExpect(status().isOk());
        mvc.perform(get("/api/adoptions/" + id)).andExpect(status().isUnauthorized());
        var other = login("other");
        mvc.perform(get("/api/adoptions/" + id).session(other)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Adoption application not found: " + id))
                .andExpect(jsonPath("$.adopterEmail").doesNotExist());
        mvc.perform(get("/api/adoptions/999999").session(other)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Adoption application not found: 999999"));
        mvc.perform(head("/api/adoptions/" + id).session(other)).andExpect(status().isNotFound());
        mvc.perform(head("/api/adoptions/" + id)).andExpect(status().isUnauthorized());
    }

    @Test void matchingHistoricalEmailDoesNotGrantOwnershipAndStaffCanStillReview() throws Exception {
        long legacy = legacy();
        var owner = login("owner");
        mvc.perform(get("/api/adoptions/" + legacy).session(owner)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/adoptions").session(owner)).andExpect(status().isOk()).andExpect(content().json("[]"));
        var staff = login("staff");
        mvc.perform(get("/api/adoptions/" + legacy).session(staff)).andExpect(status().isOk());
        // Historical email must not block a new account-owned application either.
        submit(owner, catId);
        mvc.perform(csrf(patch("/api/adoptions/" + legacy + "/approve"), staff)).andExpect(status().isOk());
        assertNull(applications.findById(legacy).orElseThrow().getAdopterAccount());
    }

    @Test void myApplicationsArePrivateAndDeterministicWithOsivDisabled() throws Exception {
        var owner = login("owner");
        long first = submit(owner, catId);
        long second = submit(owner, cats.saveAndFlush(new Cat("Second")).getId());
        long newest = submit(owner, cats.saveAndFlush(new Cat("Newest")).getId());
        submit(login("other"), catId); legacy();
        jdbc.update("update adoption_applications set created_at='2026-01-01T12:00:00Z' where id in (?,?)", first, second);
        jdbc.update("update adoption_applications set created_at='2026-01-02T12:00:00Z' where id=?", newest);
        for (int i = 0; i < 2; i++) mvc.perform(get("/api/me/adoptions").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(newest)).andExpect(jsonPath("$[1].id").value(second))
                .andExpect(jsonPath("$[2].id").value(first)).andExpect(jsonPath("$[0].catName").value("Newest"));
        mvc.perform(get("/api/me/adoptions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/adoptions").session(login("staff"))).andExpect(status().isForbidden());
    }

    @Test void adopterWithNoApplicationsGetsEmptyList() throws Exception {
        submit(login("owner"), catId);
        mvc.perform(get("/api/me/adoptions").session(login("other"))).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void concurrentSameAccountSubmissionsKeepOnePendingApplication() throws Exception {
        var principal = TestAccounts.principal(accounts.findById(ownerId).orElseThrow());
        var request = new AdoptionApplicationRequest(); request.setCatId(catId);
        var start = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            try { adoptions.submitApplication(request, principal); return 201; }
            catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(attempt); var second = executor.submit(attempt); start.countDown();
            assertEquals(List.of(201, 409), java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1, applications.count());
    }

    private long legacy() {
        var app = new AdoptionApplication(); app.setCat(cats.findById(catId).orElseThrow());
        app.setAdopterName("Historical owner text"); app.setAdopterEmail("owner@example.com");
        return applications.saveAndFlush(app).getId();
    }
    private MockHttpServletRequestBuilder input(Long cat) {
        return post("/api/adoptions").contentType(MediaType.APPLICATION_JSON).content("{\"catId\":" + cat + "}");
    }
    private long submit(MockHttpSession session, Long cat) throws Exception {
        var response = mvc.perform(csrf(input(cat), session)).andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("id").asLong();
    }
    private MockHttpSession login(String name) throws Exception {
        var response = mvc.perform(csrf(post("/api/auth/login").param("email", name + "@example.com")
                .param("password", "Test-password!"), null)).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) response.getRequest().getSession(false);
    }
    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        var init = get("/api/auth/csrf");
        if (session != null) { init.session(session); request.session(session); }
        var response = mvc.perform(init).andExpect(status().isOk()).andReturn().getResponse();
        var token = json.readTree(response.getContentAsString());
        return request.cookie(response.getCookie("XSRF-TOKEN")).header(token.get("headerName").asText(), token.get("token").asText());
    }
}
