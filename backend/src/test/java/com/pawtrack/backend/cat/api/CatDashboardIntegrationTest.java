package com.pawtrack.backend.cat.api;

import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.alert.service.AlertService;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.cat.service.CatService;
import com.pawtrack.backend.healthdata.domain.HealthData;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CatController.class)
@Import({CatService.class, com.pawtrack.backend.identity.security.SessionSecurityConfiguration.class})
@TestPropertySource(properties = "spring.autoconfigure.exclude=org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ContextConfiguration(classes = CatDashboardIntegrationTest.WebMvcTestConfig.class)
class CatDashboardIntegrationTest {
    @MockBean org.springframework.security.core.userdetails.UserDetailsService identityUsers;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatRepository catRepository;

    @MockBean
    private HealthDataRepository healthDataRepository;

    @MockBean
    private AlertRepository alertRepository;

    @MockBean
    private AlertService alertService;

    @Test
    void dashboard_returns_temperature_alert_and_streamUrl() throws Exception {
        Cat cat = new Cat("Mochi");
        cat.setHealthStatus(CatHealthStatus.NORMAL);
        cat.setStreamUrl("https://stream.example/cam/1");

        HealthData healthData = new HealthData();
        healthData.setTemperatureC(new BigDecimal("38.5"));

        when(catRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(healthDataRepository.findFirstByCatIdOrderByTsDescIdDesc(1L)).thenReturn(Optional.of(healthData));
        when(alertRepository.existsByCatIdAndStatus(1L, AlertStatus.OPEN)).thenReturn(true);

        mockMvc.perform(get("/api/cats/{id}/dashboard", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperatureC").value(38.5))
                .andExpect(jsonPath("$.hasActiveAlert").value(true))
                .andExpect(jsonPath("$.streamUrl").value("https://stream.example/cam/1"));
    }

    @Test
    void dashboard_handles_missing_health_data() throws Exception {
        Cat cat = new Cat("Nori");
        cat.setHealthStatus(CatHealthStatus.NORMAL);

        when(catRepository.findById(2L)).thenReturn(Optional.of(cat));
        when(healthDataRepository.findFirstByCatIdOrderByTsDescIdDesc(2L)).thenReturn(Optional.empty());
        when(alertRepository.existsByCatIdAndStatus(2L, AlertStatus.OPEN)).thenReturn(false);

        mockMvc.perform(get("/api/cats/{id}/dashboard", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperatureC").value(nullValue()));
    }

    @TestConfiguration
    static class WebMvcTestConfig {
    }
}
