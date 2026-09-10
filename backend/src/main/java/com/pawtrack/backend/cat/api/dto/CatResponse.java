package com.pawtrack.backend.cat.api.dto;

import java.time.OffsetDateTime;

public class CatResponse {
    private Long id;
    private String name;
    private String status;
    private String streamUrl;
    private String imageUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public CatResponse(
            Long id,
            String name,
            String status,
            String streamUrl,
            String imageUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.streamUrl = streamUrl;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getStreamUrl() { return streamUrl; }
    public String getImageUrl() { return imageUrl; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
