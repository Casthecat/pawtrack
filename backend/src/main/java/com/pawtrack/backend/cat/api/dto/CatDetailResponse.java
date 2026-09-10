package com.pawtrack.backend.cat.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;

public class CatDetailResponse {
    private Long id;
    private String name;
    private CatHealthStatus healthStatus;
    private CatAdoptionStatus adoptionStatus;
    private String streamUrl;
    private String imageUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private BigDecimal temperatureC;
    private boolean hasActiveAlert;

    public CatDetailResponse(
            Long id,
            String name,
            CatHealthStatus healthStatus,
            CatAdoptionStatus adoptionStatus,
            String streamUrl,
            String imageUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            BigDecimal temperatureC,
            boolean hasActiveAlert
    ) {
        this.id = id;
        this.name = name;
        this.healthStatus = healthStatus;
        this.adoptionStatus = adoptionStatus;
        this.streamUrl = streamUrl;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.temperatureC = temperatureC;
        this.hasActiveAlert = hasActiveAlert;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public CatHealthStatus getHealthStatus() { return healthStatus; }
    public CatAdoptionStatus getAdoptionStatus() { return adoptionStatus; }
    public String getStreamUrl() { return streamUrl; }
    public String getImageUrl() { return imageUrl; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public BigDecimal getTemperatureC() { return temperatureC; }
    public boolean isHasActiveAlert() { return hasActiveAlert; }
}
