package com.pawtrack.backend.care.domain;

import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.alert.domain.Alert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "care_records")
public class CareRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = false)
    private Cat cat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CareRecordType type;

    @Column(nullable = false, columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Cat getCat() { return cat; }
    public Alert getAlert() { return alert; }
    public CareRecordType getType() { return type; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setCat(Cat cat) { this.cat = cat; }
    public void setAlert(Alert alert) { this.alert = alert; }
    public void setType(CareRecordType type) { this.type = type; }
    public void setNote(String note) { this.note = note; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
