package com.pawtrack.backend.healthdata.domain;

import com.pawtrack.backend.cat.domain.Cat;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;


@Entity
@Table(name = "health_data")
public class HealthData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK -> cats(id)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = false)
    private Cat cat;

    @Column(name = "ts", nullable = false)
    private OffsetDateTime ts;

    @Column(name = "temperature_c", precision = 5, scale = 2)
    private BigDecimal temperatureC;

    @Column(name = "activity_level")
    private Integer activityLevel;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (ts == null) ts = OffsetDateTime.now();
    }

    // getters/setters
    public Long getId() { return id; }

    public Cat getCat() { return cat; }
    public void setCat(Cat cat) { this.cat = cat; }

    public OffsetDateTime getTs() { return ts; }
    public void setTs(OffsetDateTime ts) { this.ts = ts; }

    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }

    public Integer getActivityLevel() { return activityLevel; }
    public void setActivityLevel(Integer activityLevel) { this.activityLevel = activityLevel; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
