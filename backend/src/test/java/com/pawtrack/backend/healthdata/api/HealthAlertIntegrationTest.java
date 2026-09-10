package com.pawtrack.backend.healthdata.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.alert.domain.AlertType;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = HealthAlertIntegrationTest.TestApplication.class,
        properties = {
        "spring.datasource.url=jdbc:h2:mem:pawtrack;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springdoc.webmvc.ui.SwaggerConfig"
})
@AutoConfigureMockMvc
@Transactional
class HealthAlertIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final CatRepository catRepository;
    private final AlertRepository alertRepository;

    @Autowired
    HealthAlertIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            CatRepository catRepository,
            AlertRepository alertRepository
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.catRepository = catRepository;
        this.alertRepository = alertRepository;
    }

    @Test
    void postHealthData_createsFeverAlert_and_updatesCatStatus() throws Exception {
        Cat cat = new Cat("FeverCat");
        Cat savedCat = catRepository.save(cat);

        Map<String, Object> payload = Map.of(
                "temperatureC", new BigDecimal("40.5"),
                "activityLevel", 1
        );

        mockMvc.perform(post("/api/cats/{catId}/health", savedCat.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        List<Alert> alerts = alertRepository.findByCatIdWithCat(savedCat.getId());
        assertFalse(alerts.isEmpty());
        assertTrue(alerts.stream().anyMatch(a -> a.getType() == AlertType.FEVER));

        Cat updated = catRepository.findById(savedCat.getId()).orElseThrow();
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, updated.getHealthStatus());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.pawtrack.backend")
    @EnableJpaRepositories("com.pawtrack.backend")
    @ComponentScan(basePackages = {
            "com.pawtrack.backend.healthdata.service",
            "com.pawtrack.backend.alert.service",
            "com.pawtrack.backend.cat.service"
    })
    @Import({HealthDataController.class,
            com.pawtrack.backend.identity.security.SessionSecurityConfiguration.class,
            com.pawtrack.backend.identity.security.AccountDetailsService.class,
            com.pawtrack.backend.identity.security.PasswordConfiguration.class})
    static class TestApplication {
    }
}
