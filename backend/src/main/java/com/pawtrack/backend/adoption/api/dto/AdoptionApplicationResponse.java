package com.pawtrack.backend.adoption.api.dto;

import java.time.OffsetDateTime;

public record AdoptionApplicationResponse(
        Long id, Long catId, String catName, String adopterName, String adopterEmail,
        String status, String notes, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
