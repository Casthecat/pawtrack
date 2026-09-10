package com.pawtrack.backend.healthdata.api.dto;

import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class CreateHealthDataRequest {
    private OffsetDateTime ts;
    @Digits(integer = 3, fraction = 2, message = "Temperature must have at most 3 integer digits and 2 decimal places")
    private BigDecimal temperatureC;
    private Integer activityLevel;

    public OffsetDateTime getTs() { return ts; }
    public void setTs(OffsetDateTime ts) { this.ts = ts; }

    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }

    public Integer getActivityLevel() { return activityLevel; }
    public void setActivityLevel(Integer activityLevel) { this.activityLevel = activityLevel; }
}
