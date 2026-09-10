package com.pawtrack.backend.cat.api.mapper;

import com.pawtrack.backend.cat.api.dto.CatResponse;
import com.pawtrack.backend.cat.api.dto.CatDetailResponse;
import com.pawtrack.backend.cat.domain.Cat;

import java.math.BigDecimal;

public class CatMapper {

    private CatMapper() {}

    public static CatResponse toResponse(Cat c) {
        if (c == null) return null;
        return new CatResponse(
                c.getId(),
                c.getName(),
                c.getStatus(),
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
                c.getStatus(),
                c.getStreamUrl(),
                c.getImageUrl(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                temperatureC,
                hasActiveAlert
        );
    }
}
