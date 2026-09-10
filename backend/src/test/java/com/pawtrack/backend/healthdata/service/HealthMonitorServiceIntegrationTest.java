package com.pawtrack.backend.healthdata.service;

import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.domain.HealthData;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration,org.springframework.boot.http.converter.autoconfigure.GsonHttpMessageConvertersAutoConfiguration"
})
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class HealthMonitorServiceIntegrationTest {

    private final HealthMonitorService healthMonitorService;
    private final CatRepository catRepository;
    private final HealthDataRepository healthDataRepository;
    private final AlertRepository alertRepository;

    @Autowired
    HealthMonitorServiceIntegrationTest(
            HealthMonitorService healthMonitorService,
            CatRepository catRepository,
            HealthDataRepository healthDataRepository,
            AlertRepository alertRepository
    ) {
        this.healthMonitorService = healthMonitorService;
        this.catRepository = catRepository;
        this.healthDataRepository = healthDataRepository;
        this.alertRepository = alertRepository;
    }

    @Test
    void analyzeAndAlert_createsAlert_and_updatesCatStatus() {
        Cat cat = new Cat("Mochi");
        Cat savedCat = catRepository.save(cat);

        HealthData data = new HealthData();
        data.setCat(savedCat);
        data.setTemperatureC(new BigDecimal("40.0"));
        healthDataRepository.save(data);

        boolean hasFever = healthMonitorService.analyzeAndAlert(savedCat.getId());

        assertTrue(hasFever);

        List<Alert> alerts = alertRepository.findByCatIdWithCat(savedCat.getId());
        assertFalse(alerts.isEmpty());
        assertNotNull(alerts.get(0).getId());

        Cat updatedCat = catRepository.findById(savedCat.getId()).orElseThrow();
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, updatedCat.getHealthStatus());
    }
}
