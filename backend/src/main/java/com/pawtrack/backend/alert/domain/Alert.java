package com.pawtrack.backend.alert.domain;

import com.pawtrack.backend.cat.domain.Cat;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK -> cats(id)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = false)
    private Cat cat;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private AlertType type; // e.g. FEVER

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AlertSeverity severity; // LOW/MEDIUM/HIGH

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status; // OPEN/CLOSED

    @Column(name = "message")
    private String message;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;

        if (status == null) status = AlertStatus.OPEN;
        if (severity == null) severity = AlertSeverity.MEDIUM;
        // Alert type is assigned explicitly by the creating service.
    }

    // getters/setters
    public Long getId() { return id; }

    public Cat getCat() { return cat; }
    public void setCat(Cat cat) { this.cat = cat; }

    public AlertType getType() { return type; }
    public void setType(AlertType type) { this.type = type; }

    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity severity) { this.severity = severity; }

    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

}
