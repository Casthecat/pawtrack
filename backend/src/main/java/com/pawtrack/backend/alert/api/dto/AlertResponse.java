package com.pawtrack.backend.alert.api.dto;

import java.time.OffsetDateTime;

public class AlertResponse {
    private Long id;
    private Long catId;
    private String catName;
    private String type;
    private String severity;
    private String status;
    private String message;
    private OffsetDateTime createdAt;
    private OffsetDateTime resolvedAt;

    public AlertResponse(
            Long id, Long catId, String catName,
            String type, String severity, String status,
            String message, OffsetDateTime createdAt, OffsetDateTime resolvedAt
    ) {
        this.id = id;
        this.catId = catId;
        this.catName = catName;
        this.type = type;
        this.severity = severity;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public Long getId() { return id; }
    public Long getCatId() { return catId; }
    public String getCatName() { return catName; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
}
