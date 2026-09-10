package com.pawtrack.backend.cat.api.mapper;

import com.pawtrack.backend.cat.api.dto.CatResponse;
import com.pawtrack.backend.cat.api.dto.CatDetailResponse;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;

import java.math.BigDecimal;

public class CatMapper {

    private CatMapper() {}

    public static CatResponse toResponse(Cat c) {
        if (c == null) return null;
        return new CatResponse(
                c.getId(),
                c.getName(),
                compatibilityStatus(c),
                c.getStreamUrl(),
                c.getImageUrl(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    public static CatDetailResponse toDetailResponse(Cat c, BigDecimal temperatureC, boolean hasActiveAlert) {
        if (c == null) return null;
        return new CatDetailResponse(
                c.getId(),
                c.getName(),
                compatibilityStatus(c),
                c.getStreamUrl(),
                c.getImageUrl(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                temperatureC,
                hasActiveAlert
        );
    }
    // Transitional P3.1 DTO projection; never use this lossy value for business rules.
    private static String compatibilityStatus(Cat cat) {
        if (cat.getAdoptionStatus() == CatAdoptionStatus.ADOPTED) return "ADOPTED";
        return switch (cat.getHealthStatus()) {
            case SICK -> "SICK";
            case UNDER_OBSERVATION -> "UNDER_OBSERVATION";
            case NORMAL -> "NORMAL";
        };
    }
}
