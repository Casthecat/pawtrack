package com.pawtrack.backend.healthdata.api.mapper;

import com.pawtrack.backend.healthdata.api.dto.HealthDataResponse;
import com.pawtrack.backend.healthdata.domain.HealthData;

public class HealthDataMapper {

    private HealthDataMapper() {}

    public static HealthDataResponse toResponse(HealthData hd) {
        if (hd == null) return null;
        return new HealthDataResponse(
                hd.getId(),
                hd.getCat() == null ? null : hd.getCat().getId(),
                hd.getTs(),
                hd.getTemperatureC(),
                hd.getActivityLevel(),
                hd.getCreatedAt()
        );
    }
}
