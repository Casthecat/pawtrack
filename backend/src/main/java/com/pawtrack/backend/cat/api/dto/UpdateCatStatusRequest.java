package com.pawtrack.backend.cat.api.dto;

public class UpdateCatStatusRequest {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Pattern(regexp = "NORMAL|UNDER_OBSERVATION|SICK|ADOPTABLE")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
