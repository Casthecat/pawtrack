package com.pawtrack.backend.care.service;

import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.care.api.dto.AlertResolutionRequest;
import com.pawtrack.backend.care.api.dto.AlertResolutionResponse;
import com.pawtrack.backend.care.domain.CareRecord;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AlertResolutionService {
    private final CatRepository cats;
    private final AlertRepository alerts;
    private final CareRecordRepository careRecords;

    @Transactional
    public AlertResolutionResponse resolve(Long alertId, AlertResolutionRequest request) {
        Long catId = alerts.findCatIdByAlertId(alertId)
                .orElseThrow(() -> new EntityNotFoundException("Alert not found: " + alertId));
        // Shared global order: lock Cat before loading mutable Alert state.
        Cat cat = cats.findByIdForUpdate(catId)
                .orElseThrow(() -> new EntityNotFoundException("Cat not found: " + catId));
        Alert alert = alerts.findById(alertId)
                .orElseThrow(() -> new EntityNotFoundException("Alert not found: " + alertId));
        if (alert.getStatus() != AlertStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This alert is already closed. Please refresh the cat's health timeline.");
        }

        OffsetDateTime resolvedAt = OffsetDateTime.now();
        alert.setStatus(AlertStatus.CLOSED);
        alert.setResolvedAt(resolvedAt);
        CareRecord care = new CareRecord();
        care.setCat(cat);
        care.setAlert(alert);
        care.setType(request.careType());
        care.setNote(request.note().trim());
        care.setCreatedAt(resolvedAt);
        // Flush includes the alert update, so the remaining-open query sees CLOSED.
        careRecords.saveAndFlush(care);

        if (!alerts.existsByCatIdAndStatus(catId, AlertStatus.OPEN)
                && cat.getHealthStatus() == CatHealthStatus.UNDER_OBSERVATION) {
            cat.setHealthStatus(CatHealthStatus.NORMAL);
        }
        return new AlertResolutionResponse(alertId, catId, alert.getStatus(), resolvedAt, care.getId());
    }
}
