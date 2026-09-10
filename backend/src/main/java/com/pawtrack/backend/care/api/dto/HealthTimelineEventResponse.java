package com.pawtrack.backend.care.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HealthTimelineEventResponse(
        HealthTimelineEventKind eventKind,
        Long sourceId,
        OffsetDateTime occurredAt,
        String eventType,
        String description,
        BigDecimal temperatureC,
        Integer activityLevel,
        String alertStatus,
        String alertSeverity,
        Long relatedAlertId
) {
}
