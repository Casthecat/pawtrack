package com.pawtrack.backend.cat.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "cats")
public class Cat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 30)
    private CatHealthStatus healthStatus = CatHealthStatus.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "adoption_status", nullable = false, length = 30)
    private CatAdoptionStatus adoptionStatus = CatAdoptionStatus.AVAILABLE;

    @Column(name = "stream_url", length = 500)
    private String streamUrl;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Cat() {}

    public Cat(String name) {
        this.name = name;
    }

    public boolean isAvailableForAdoption() {
        return adoptionStatus == CatAdoptionStatus.AVAILABLE && healthStatus == CatHealthStatus.NORMAL;
    }

    // getters/setters
    public Long getId() { return id; }
    public String getName() { return name; }
    public CatHealthStatus getHealthStatus() { return healthStatus; }
    public CatAdoptionStatus getAdoptionStatus() { return adoptionStatus; }
    public String getStreamUrl() { return streamUrl; }
    public String getImageUrl() { return imageUrl; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setHealthStatus(CatHealthStatus healthStatus) { this.healthStatus = Objects.requireNonNull(healthStatus); }
    public void setAdoptionStatus(CatAdoptionStatus adoptionStatus) { this.adoptionStatus = Objects.requireNonNull(adoptionStatus); }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
