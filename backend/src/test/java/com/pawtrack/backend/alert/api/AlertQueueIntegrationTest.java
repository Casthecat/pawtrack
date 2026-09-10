package com.pawtrack.backend.alert.api;

import com.pawtrack.backend.alert.domain.*;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.pawtrack.backend.support.StaffRegression
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:alert-queue;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AlertQueueIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;
    @Autowired CatRepository cats;
    @Autowired AlertRepository alerts;

    @BeforeEach
    void reset() {
        alerts.deleteAllInBatch();
        cats.deleteAllInBatch();
    }

    @Test
    void openOnlyQueueHasCatDtoFieldsAndDeterministicOrderingWithOsivDisabled() throws Exception {
        assertEquals("false", environment.getProperty("spring.jpa.open-in-view"));
        Cat nori = cats.saveAndFlush(new Cat("Nori"));
        Cat cleo = cats.saveAndFlush(new Cat("Cleo"));
        Alert older = save(nori, AlertStatus.OPEN, "2026-09-10T09:00:00Z");
        Alert firstTie = save(nori, AlertStatus.OPEN, "2026-09-10T10:00:00Z");
        Alert secondTie = save(cleo, AlertStatus.OPEN, "2026-09-10T10:00:00Z");
        save(nori, AlertStatus.CLOSED, "2026-09-10T11:00:00Z");
        mvc.perform(get("/api/alerts").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(secondTie.getId()))
                .andExpect(jsonPath("$[0].catId").value(cleo.getId()))
                .andExpect(jsonPath("$[0].catName").value("Cleo"))
                .andExpect(jsonPath("$[0].type").value("FEVER"))
                .andExpect(jsonPath("$[0].severity").value("HIGH"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].message").value("Temperature observation"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].resolvedAt").isEmpty())
                .andExpect(jsonPath("$[1].id").value(firstTie.getId()))
                .andExpect(jsonPath("$[2].id").value(older.getId()));
        mvc.perform(get("/api/alerts"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void emptyQueueIsAnArrayEvenWhenClosedAlertsExist() throws Exception {
        save(cats.saveAndFlush(new Cat("Nori")), AlertStatus.CLOSED, "2026-09-10T09:00:00Z");
        mvc.perform(get("/api/alerts").param("status", "OPEN"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void simpleClosedFilterIncludesResolutionMetadata() throws Exception {
        Cat nori = cats.saveAndFlush(new Cat("Nori"));
        Alert closed = save(nori, AlertStatus.CLOSED, "2026-09-10T09:00:00Z");
        closed.setResolvedAt(OffsetDateTime.parse("2026-09-10T10:00:00Z"));
        alerts.saveAndFlush(closed);
        save(nori, AlertStatus.OPEN, "2026-09-10T11:00:00Z");
        mvc.perform(get("/api/alerts").param("status", "CLOSED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CLOSED"))
                .andExpect(jsonPath("$[0].resolvedAt").exists());
    }

    @Test
    void invalidStatusUsesNormalBadRequestResponse() throws Exception {
        mvc.perform(get("/api/alerts").param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request. Check the JSON, identifier, or status filter."));
    }

    private Alert save(Cat cat, AlertStatus status, String timestamp) {
        Alert alert = new Alert();
        alert.setCat(cat);
        alert.setType(AlertType.FEVER);
        alert.setSeverity(AlertSeverity.HIGH);
        alert.setStatus(status);
        alert.setMessage("Temperature observation");
        alert.setCreatedAt(OffsetDateTime.parse(timestamp));
        return alerts.saveAndFlush(alert);
    }
}
