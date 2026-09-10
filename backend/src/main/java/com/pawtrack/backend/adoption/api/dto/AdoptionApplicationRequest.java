package com.pawtrack.backend.adoption.api.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.*;

@Getter
@Setter
public class AdoptionApplicationRequest {
    @NotNull @Positive
    private Long catId;
    @NotBlank @Size(max = 120)
    private String adopterName;
    @NotBlank @Email @Size(max = 200)
    private String adopterEmail;
    @Size(max = 2000)
    private String notes;
}
