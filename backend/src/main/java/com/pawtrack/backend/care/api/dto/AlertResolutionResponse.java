package com.pawtrack.backend.care.api.dto;

import com.pawtrack.backend.alert.domain.AlertStatus;
import java.time.OffsetDateTime;

public record AlertResolutionResponse(
        Long alertId,
        Long catId,
        AlertStatus status,
        OffsetDateTime resolvedAt,
        Long careRecordId
) {
}
