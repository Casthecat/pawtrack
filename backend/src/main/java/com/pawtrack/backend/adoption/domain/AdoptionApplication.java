package com.pawtrack.backend.adoption.domain;

import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.identity.domain.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "adoption_applications")
@Getter
@Setter
public class AdoptionApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = false)
    private Cat cat;

    // NULL only for historical applications; never infer ownership from snapshot email.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adopter_account_id")
    private UserAccount adopterAccount;

    @Column(name = "adopter_name", nullable = false, length = 120)
    private String adopterName;

    @Column(name = "adopter_email", nullable = false, length = 200)
    private String adopterEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdoptionStatus status = AdoptionStatus.PENDING;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (status == null) status = AdoptionStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
