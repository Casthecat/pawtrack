package com.pawtrack.backend.healthdata.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class HealthDataResponse {
    private Long id;
    private Long catId;
    private OffsetDateTime ts;
    private BigDecimal temperatureC;
    private Integer activityLevel;
    private OffsetDateTime createdAt;

    public HealthDataResponse(
            Long id, Long catId, OffsetDateTime ts,
            BigDecimal temperatureC, Integer activityLevel,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.catId = catId;
        this.ts = ts;
        this.temperatureC = temperatureC;
        this.activityLevel = activityLevel;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getCatId() { return catId; }
    public OffsetDateTime getTs() { return ts; }
    public BigDecimal getTemperatureC() { return temperatureC; }
    public Integer getActivityLevel() { return activityLevel; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
