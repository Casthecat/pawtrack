package com.pawtrack.backend.adoption.api.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.*;

@Getter
@Setter
public class AdoptionApplicationRequest {
    @NotNull @Positive
    private Long catId;
    @Size(max = 2000)
    private String notes;
}
