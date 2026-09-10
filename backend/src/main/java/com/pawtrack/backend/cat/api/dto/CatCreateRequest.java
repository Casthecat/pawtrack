package com.pawtrack.backend.cat.api.dto;

public class CatCreateRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
