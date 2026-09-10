package com.pawtrack.backend.care.api.mapper;

import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.care.api.dto.HealthTimelineEventKind;
import com.pawtrack.backend.care.api.dto.HealthTimelineEventResponse;
import com.pawtrack.backend.care.domain.CareRecord;
import com.pawtrack.backend.healthdata.domain.HealthData;

public final class HealthTimelineMapper {

    private HealthTimelineMapper() {}

    public static HealthTimelineEventResponse fromObservation(HealthData observation) {
        return new HealthTimelineEventResponse(
                HealthTimelineEventKind.HEALTH_OBSERVATION,
                observation.getId(),
                observation.getTs(),
                "VITALS",
                null,
                observation.getTemperatureC(),
                observation.getActivityLevel(),
                null,
                null,
                null
        );
    }

    public static HealthTimelineEventResponse fromAlert(Alert alert) {
        return new HealthTimelineEventResponse(
                HealthTimelineEventKind.ALERT,
                alert.getId(),
                alert.getCreatedAt(),
                alert.getType().name(),
                alert.getMessage(),
                null,
                null,
                alert.getStatus().name(),
                alert.getSeverity().name(),
                null
        );
    }

    public static HealthTimelineEventResponse fromCareRecord(CareRecord record) {
        return new HealthTimelineEventResponse(
                HealthTimelineEventKind.CARE_RECORD,
                record.getId(),
                record.getCreatedAt(),
                record.getType().name(),
                record.getNote(),
                null,
                null,
                null,
                null,
                record.getAlert() == null ? null : record.getAlert().getId()
        );
    }
}
