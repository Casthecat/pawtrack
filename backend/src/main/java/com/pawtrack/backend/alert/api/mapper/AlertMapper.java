package com.pawtrack.backend.alert.api.mapper;

import com.pawtrack.backend.alert.api.dto.AlertResponse;
import com.pawtrack.backend.alert.domain.Alert;

public class AlertMapper {
    public static AlertResponse toResponse(Alert a) {
        return new AlertResponse(
                a.getId(),
                a.getCat() == null ? null : a.getCat().getId(),
                a.getCat() == null ? null : a.getCat().getName(),
                a.getType() == null ? null : a.getType().toString(),
                a.getSeverity() == null ? null : a.getSeverity().toString(),
                a.getStatus() == null ? null : a.getStatus().toString(),
                a.getMessage(),
                a.getCreatedAt(),
                a.getResolvedAt()
        );
    }
}
