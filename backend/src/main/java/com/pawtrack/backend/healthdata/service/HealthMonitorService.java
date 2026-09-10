package com.pawtrack.backend.healthdata.service;

import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.alert.domain.AlertSeverity;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.domain.AlertType;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.domain.HealthData;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class HealthMonitorService {

    private static final BigDecimal FEVER_THRESHOLD_C = new BigDecimal("39.5");

    private final HealthDataRepository healthDataRepository;
    private final AlertRepository alertRepository;
    private final CatRepository catRepository;

    @Transactional
    public boolean analyzeAndAlert(Long catId) {
        Cat cat = catRepository.findByIdForUpdate(catId)
                .orElseThrow(() -> new EntityNotFoundException("Cat not found: " + catId));
        Optional<HealthData> latest = healthDataRepository.findFirstByCatIdOrderByTsDescIdDesc(catId);
        if (latest.isEmpty()) {
            return false;
        }

        BigDecimal temperatureC = latest.get().getTemperatureC();
        if (temperatureC != null && temperatureC.compareTo(FEVER_THRESHOLD_C) > 0) {
            log.warn("High temperature detected for cat {}: {}°C", catId, temperatureC);
            if (alertRepository.findLatestOpenByCatAndType(catId, AlertType.FEVER).isEmpty()) {
                Alert alert = new Alert();
                alert.setCat(cat);
                alert.setType(AlertType.FEVER);
                alert.setSeverity(AlertSeverity.HIGH);
                alert.setStatus(AlertStatus.OPEN);
                alert.setMessage("Automatic alert: High temperature detected (" + temperatureC + "°C)");
                alertRepository.save(alert);
            }
            if (cat.getHealthStatus() != CatHealthStatus.SICK) {
                cat.setHealthStatus(CatHealthStatus.UNDER_OBSERVATION);
                catRepository.save(cat);
            }
            return true;
        }

        return false;
    }
}
