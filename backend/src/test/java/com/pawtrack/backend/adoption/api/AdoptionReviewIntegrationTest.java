package com.pawtrack.backend.adoption.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationResponse;
import com.pawtrack.backend.adoption.domain.AdoptionStatus;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.adoption.service.AdoptionService;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import com.pawtrack.backend.healthdata.service.HealthDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AdoptionReviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdoptionService service;
    @Autowired AdoptionApplicationRepository applications;
    @Autowired CatRepository cats;
    @Autowired AlertRepository alerts;
    @Autowired HealthDataRepository healthRecords;
    @Autowired HealthDataService health;
    private Cat cat;

    @BeforeEach
    void setUp() {
        cat = cats.save(new Cat("Test companion"));
    }

    private AdoptionApplicationResponse apply(String email) {
        AdoptionApplicationRequest req = new AdoptionApplicationRequest();
        req.setCatId(cat.getId());
        req.setAdopterName("Alex");
        req.setAdopterEmail(email);
        req.setNotes("A quiet home.");
        return service.submitApplication(req);
    }

    @Test
    void fullHttpFlow_returnsDtos_filtersQueue_andClosesCompetingApplications() throws Exception {
        String body = json.writeValueAsString(Map.of("catId", cat.getId(), "adopterName", " Alex ",
                "adopterEmail", "ALEX@example.com", "notes", "A quiet home."));
        JsonNode submitted = json.readTree(mvc.perform(post("/api/adoptions").header("Origin", "http://127.0.0.1:5173").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catName").value("Test companion"))
                .andExpect(jsonPath("$.adopterName").value("Alex"))
                .andExpect(jsonPath("$.adopterEmail").value("alex@example.com"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        long id = submitted.get("id").asLong();
        AdoptionApplicationResponse competing = apply("other@example.com");
        mvc.perform(get("/api/adoptions").param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(patch("/api/adoptions/{id}/approve", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/adoptions/{id}", competing.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/adoptions").param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertEquals(CatAdoptionStatus.ADOPTED, cats.findById(cat.getId()).orElseThrow().getAdoptionStatus());
        assertFalse(service.getById(id).updatedAt().isBefore(service.getById(id).createdAt()));
    }

    @Test
    void invalidRequests_return400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/adoptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"catId\":-1,\"adopterName\":\" \",\"adopterEmail\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.catId").exists())
                .andExpect(jsonPath("$.errors.adopterName").exists()).andExpect(jsonPath("$.errors.adopterEmail").exists());
        mvc.perform(post("/api/adoptions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cats").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/adoptions").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/api/adoptions").param("status", "UNKNOWN")).andExpect(status().isBadRequest());
        assertEquals(0, applications.count());
    }

    @Test
    void unknownApplication_returns404() throws Exception {
        mvc.perform(get("/api/adoptions/999999")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/adoptions/999999/approve")).andExpect(status().isNotFound());
    }

    @Test
    void duplicatePendingEmail_isCaseInsensitive_andCanReapplyAfterRejection() {
        AdoptionApplicationResponse first = apply("alex@example.com");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> apply(" ALEX@example.com ")).getStatusCode().value());
        service.rejectApplication(first.id());
        assertEquals("PENDING", apply("alex@example.com").status());
        assertEquals(CatHealthStatus.NORMAL, cats.findById(cat.getId()).orElseThrow().getHealthStatus());
    }

    @Test
    void finalDecisionsCannotBeReversed_orRepeated() throws Exception {
        AdoptionApplicationResponse rejected = apply("first@example.com");
        service.rejectApplication(rejected.id());
        mvc.perform(patch("/api/adoptions/{id}/approve", rejected.id())).andExpect(status().isConflict());
        mvc.perform(patch("/api/adoptions/{id}/reject", rejected.id())).andExpect(status().isConflict());
        AdoptionApplicationResponse approved = apply("second@example.com");
        service.approveApplication(approved.id());
        mvc.perform(patch("/api/adoptions/{id}/approve", approved.id())).andExpect(status().isConflict());
        mvc.perform(patch("/api/adoptions/{id}/reject", approved.id())).andExpect(status().isConflict());
        mvc.perform(patch("/api/cats/{id}/status", cat.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"NORMAL\"}"))
                .andExpect(status().isConflict());
        assertEquals(CatAdoptionStatus.ADOPTED, cats.findById(cat.getId()).orElseThrow().getAdoptionStatus());
        assertEquals("APPROVED", service.getById(approved.id()).status());
    }

    @Test
    void healthAlertBlocksSubmissionAndApproval_withoutChangingPendingApplication() {
        AdoptionApplicationResponse pending = apply("alex@example.com");
        health.create(cat.getId(), null, new BigDecimal("40.0"), 1);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.approveApplication(pending.id())).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> apply("other@example.com"));
        assertEquals("PENDING", service.getById(pending.id()).status());
    }

    @Test
    void unavailableStatesCannotReceiveApplications() {
        for (CatHealthStatus state : List.of(CatHealthStatus.SICK, CatHealthStatus.UNDER_OBSERVATION)) {
            cat.setHealthStatus(state);
            cats.saveAndFlush(cat);
            assertThrows(ResponseStatusException.class, () -> apply("alex@example.com"));
        }
        cat.setHealthStatus(CatHealthStatus.NORMAL);
        cat.setAdoptionStatus(CatAdoptionStatus.ADOPTED);
        cats.saveAndFlush(cat);
        assertThrows(ResponseStatusException.class, () -> apply("alex@example.com"));
        assertEquals(0, applications.count());
    }

    @Test
    void simultaneousApprovalsHaveExactlyOneWinner() throws Exception {
        AdoptionApplicationResponse first = apply("one@example.com");
        AdoptionApplicationResponse second = apply("two@example.com");
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> approveAfter(start, first.id())),
                    executor.submit(() -> approveAfter(start, second.id())));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        assertEquals(1, service.list(AdoptionStatus.APPROVED).size());
        assertEquals(1, service.list(AdoptionStatus.REJECTED).size());
        assertEquals(CatAdoptionStatus.ADOPTED, cats.findById(cat.getId()).orElseThrow().getAdoptionStatus());
    }

    private boolean approveAfter(CountDownLatch start, Long id) throws InterruptedException {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            service.approveApplication(id);
            return true;
        } catch (ResponseStatusException ex) {
            assertEquals(409, ex.getStatusCode().value());
            return false;
        }
    }

    @Test
    void feverDeduplicates_canReopenAfterClose_andPreservesAdoptedState() throws Exception {
        service.approveApplication(apply("alex@example.com").id());
        health.create(cat.getId(), null, new BigDecimal("40.0"), 1);
        health.create(cat.getId(), null, new BigDecimal("40.2"), 1);
        assertEquals(1, alerts.count());
        assertEquals(CatAdoptionStatus.ADOPTED, cats.findById(cat.getId()).orElseThrow().getAdoptionStatus());
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, cats.findById(cat.getId()).orElseThrow().getHealthStatus());
        var alert = alerts.findAll().getFirst();
        mvc.perform(patch("/api/alerts/{id}/resolve", alert.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careType\":\"CHECKUP\",\"note\":\"Staff rechecked temperature.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.catId").value(cat.getId()))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        assertEquals(CatAdoptionStatus.ADOPTED, cats.findById(cat.getId()).orElseThrow().getAdoptionStatus());
        assertEquals(CatHealthStatus.NORMAL, cats.findById(cat.getId()).orElseThrow().getHealthStatus());
        mvc.perform(patch("/api/alerts/{id}/resolve", alert.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careType\":\"CHECKUP\",\"note\":\"Staff rechecked temperature.\"}"))
                .andExpect(status().isConflict());
        health.create(cat.getId(), null, new BigDecimal("40.1"), 1);
        assertEquals(2, alerts.count());
        assertEquals(CatAdoptionStatus.ADOPTED, cats.findById(cat.getId()).orElseThrow().getAdoptionStatus());
    }

    @Test
    void browserCanPreflightPatch() throws Exception {
        mvc.perform(options("/api/adoptions/1/approve").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
