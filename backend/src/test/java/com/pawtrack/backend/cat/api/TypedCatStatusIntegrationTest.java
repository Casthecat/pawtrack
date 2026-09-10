package com.pawtrack.backend.cat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.BackendApplication;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import com.pawtrack.backend.healthdata.service.HealthDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class,
        properties = "spring.datasource.url=jdbc:h2:mem:typed-cat-status;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TypedCatStatusIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CatRepository cats;
    @Autowired AdoptionApplicationRepository applications;
    @Autowired AlertRepository alerts;
    @Autowired HealthDataRepository observations;
    @Autowired CareRecordRepository careRecords;
    @Autowired HealthDataService health;
    private Cat cat;

    @BeforeEach
    void setup() {
        careRecords.deleteAllInBatch();
        applications.deleteAllInBatch();
        alerts.deleteAllInBatch();
        observations.deleteAllInBatch();
        cats.deleteAllInBatch();
        cat = cats.saveAndFlush(new Cat("Typed Nori"));
    }

    @Test
    void constructorsAndHttpCreationDefaultToNormalAvailable() throws Exception {
        for (Cat value : new Cat[]{new Cat(), new Cat("New cat"), cat}) {
            assertEquals(CatHealthStatus.NORMAL, value.getHealthStatus());
            assertEquals(CatAdoptionStatus.AVAILABLE, value.getAdoptionStatus());
            assertTrue(value.isAvailableForAdoption());
        }
        var body = mvc.perform(post("/api/cats").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New arrival\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.healthStatus").value("NORMAL"))
                .andExpect(jsonPath("$.adoptionStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Cat stored = cats.findById(json.readTree(body).get("id").asLong()).orElseThrow();
        assertEquals(CatHealthStatus.NORMAL, stored.getHealthStatus());
        assertEquals(CatAdoptionStatus.AVAILABLE, stored.getAdoptionStatus());
    }

    @ParameterizedTest
    @CsvSource({"AVAILABLE,NORMAL,201", "AVAILABLE,UNDER_OBSERVATION,409",
            "AVAILABLE,SICK,409", "ADOPTED,NORMAL,409",
            "ADOPTED,UNDER_OBSERVATION,409", "ADOPTED,SICK,409"})
    void persistedCombinationsExposeBothDimensionsAndEligibility(CatAdoptionStatus adoption,
            CatHealthStatus healthStatus, int applicationStatus) throws Exception {
        cat.setAdoptionStatus(adoption);
        cat.setHealthStatus(healthStatus);
        cats.saveAndFlush(cat);
        for (String suffix : new String[]{"", "/dashboard"}) {
            mvc.perform(get("/api/cats/" + cat.getId() + suffix)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").doesNotExist())
                    .andExpect(jsonPath("$.healthStatus").value(healthStatus.name()))
                    .andExpect(jsonPath("$.adoptionStatus").value(adoption.name()));
        }
        mvc.perform(get("/api/cats")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].healthStatus").value(healthStatus.name()))
                .andExpect(jsonPath("$[0].adoptionStatus").value(adoption.name()));
        apply("first@example.com", applicationStatus);
        var stored = reload();
        assertEquals(healthStatus, stored.getHealthStatus());
        assertEquals(adoption, stored.getAdoptionStatus());
        assertEquals(applicationStatus == 201 ? 1 : 0, applications.count());
    }

    @Test
    void approvalChangesOnlyAdoptionAndRejectsCompetingApplication() throws Exception {
        long first = apply("first@example.com", 201);
        long second = apply("second@example.com", 201);
        CatHealthStatus previousHealth = reload().getHealthStatus();
        mvc.perform(patch("/api/adoptions/{id}/approve", first)).andExpect(status().isOk());
        assertEquals(previousHealth, reload().getHealthStatus());
        assertEquals(CatAdoptionStatus.ADOPTED, reload().getAdoptionStatus());
        assertEquals("APPROVED", applications.findById(first).orElseThrow().getStatus().name());
        assertEquals("REJECTED", applications.findById(second).orElseThrow().getStatus().name());
    }

    @ParameterizedTest
    @EnumSource(value = CatHealthStatus.class, names = {"UNDER_OBSERVATION", "SICK"})
    void approvalCannotResetUnhealthyCatToMakeItEligible(CatHealthStatus healthStatus) throws Exception {
        long pending = apply("pending@example.com", 201);
        cat.setHealthStatus(healthStatus);
        cats.saveAndFlush(cat);
        mvc.perform(patch("/api/adoptions/{id}/approve", pending)).andExpect(status().isConflict());
        assertEquals(healthStatus, reload().getHealthStatus());
        assertEquals(CatAdoptionStatus.AVAILABLE, reload().getAdoptionStatus());
        assertEquals("PENDING", applications.findById(pending).orElseThrow().getStatus().name());
    }

    @ParameterizedTest
    @CsvSource({"AVAILABLE,NORMAL,UNDER_OBSERVATION", "ADOPTED,NORMAL,UNDER_OBSERVATION",
            "AVAILABLE,SICK,SICK", "ADOPTED,SICK,SICK"})
    void feverPreservesAdoptionAndSickStateWhileDeduplicating(CatAdoptionStatus adoption,
            CatHealthStatus initialHealth, CatHealthStatus expectedHealth) {
        cat.setAdoptionStatus(adoption);
        cat.setHealthStatus(initialHealth);
        cats.saveAndFlush(cat);
        health.create(cat.getId(), null, new BigDecimal("40.00"), 1);
        health.create(cat.getId(), null, new BigDecimal("40.20"), 1);
        assertEquals(expectedHealth, reload().getHealthStatus());
        assertEquals(adoption, reload().getAdoptionStatus());
        assertEquals(1, alerts.count());
        assertEquals(AlertStatus.OPEN, alerts.findAll().getFirst().getStatus());
    }

    @ParameterizedTest
    @CsvSource({"AVAILABLE,NORMAL", "AVAILABLE,UNDER_OBSERVATION", "AVAILABLE,SICK",
            "ADOPTED,NORMAL", "ADOPTED,UNDER_OBSERVATION", "ADOPTED,SICK"})
    void legacyStatusPatchIsUnavailableAndCannotWriteEitherDimension(CatAdoptionStatus adoption,
            CatHealthStatus healthStatus) throws Exception {
        cat.setAdoptionStatus(adoption);
        cat.setHealthStatus(healthStatus);
        cats.saveAndFlush(cat);
        mvc.perform(patch("/api/cats/{id}/status", cat.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADOPTED\"}"))
                .andExpect(status().isNotFound());
        assertEquals(adoption, reload().getAdoptionStatus());
        assertEquals(healthStatus, reload().getHealthStatus());
    }

    @Test
    void normalHealthStillCannotBypassAnOpenAlert() throws Exception {
        health.create(cat.getId(), null, new BigDecimal("40.00"), 1);
        cat = reload();
        cat.setHealthStatus(CatHealthStatus.NORMAL);
        cats.saveAndFlush(cat);
        assertEquals(CatHealthStatus.NORMAL, reload().getHealthStatus());
        assertEquals(CatAdoptionStatus.AVAILABLE, reload().getAdoptionStatus());
        apply("blocked@example.com", 409);
        assertEquals(0, applications.count());
    }

    private Cat reload() { return cats.findById(cat.getId()).orElseThrow(); }

    private long apply(String email, int expected) throws Exception {
        var result = mvc.perform(post("/api/adoptions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"catId":%d,"adopterName":"Demo applicant","adopterEmail":"%s"}
                        """.formatted(cat.getId(), email)))
                .andExpect(status().is(expected)).andReturn().getResponse();
        return expected == 201 ? json.readTree(result.getContentAsString()).get("id").asLong() : -1;
    }
}
