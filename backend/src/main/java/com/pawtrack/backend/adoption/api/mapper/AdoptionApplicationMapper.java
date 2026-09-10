package com.pawtrack.backend.adoption.api.mapper;

import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationResponse;
import com.pawtrack.backend.adoption.domain.AdoptionApplication;

public class AdoptionApplicationMapper {

    private AdoptionApplicationMapper() {}

    public static AdoptionApplicationResponse toResponse(AdoptionApplication app) {
        if (app == null) return null;
        return new AdoptionApplicationResponse(
                app.getId(),
                app.getCat().getId(),
                app.getCat().getName(),
                app.getAdopterName(),
                app.getAdopterEmail(),
                app.getStatus().name(),
                app.getNotes(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}
