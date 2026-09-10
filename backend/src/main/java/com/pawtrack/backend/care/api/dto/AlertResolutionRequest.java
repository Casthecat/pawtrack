package com.pawtrack.backend.care.api.dto;

import com.pawtrack.backend.care.domain.CareRecordType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlertResolutionRequest(
        @NotNull CareRecordType careType,
        @NotBlank @Size(max = 2000) String note
) {
}
