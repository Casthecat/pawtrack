package com.pawtrack.backend.adoption.service;

import com.pawtrack.backend.adoption.domain.AdoptionApplication;
import com.pawtrack.backend.adoption.domain.AdoptionStatus;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdoptionFlowIntegrationTest {

    @Autowired
    private AdoptionService adoptionService;

    @Autowired
    private AdoptionApplicationRepository adoptionApplicationRepository;

    @Autowired
    private CatRepository catRepository;

    @Test
    void approveAndReject_flow_updatesStatusCorrectly() {
        Cat cat = new Cat("Nori");
        cat.setHealthStatus(CatHealthStatus.NORMAL);
        Cat savedCat = catRepository.save(cat);

        AdoptionApplication pending = new AdoptionApplication();
        pending.setCat(savedCat);
        pending.setAdopterName("Alice");
        pending.setAdopterEmail("alice@example.com");
        pending.setStatus(AdoptionStatus.PENDING);
        AdoptionApplication savedPending = adoptionApplicationRepository.save(pending);

        adoptionService.approveApplication(savedPending.getId());

        AdoptionApplication approved = adoptionApplicationRepository.findById(savedPending.getId()).orElseThrow();
        assertEquals(AdoptionStatus.APPROVED, approved.getStatus());

        Cat adoptedCat = catRepository.findById(savedCat.getId()).orElseThrow();
        assertEquals(CatAdoptionStatus.ADOPTED, adoptedCat.getAdoptionStatus());

        AdoptionApplication rejected = new AdoptionApplication();
        rejected.setCat(adoptedCat);
        rejected.setAdopterName("Bob");
        rejected.setAdopterEmail("bob@example.com");
        rejected.setStatus(AdoptionStatus.PENDING);
        AdoptionApplication savedRejected = adoptionApplicationRepository.save(rejected);

        adoptionService.rejectApplication(savedRejected.getId());

        AdoptionApplication rejectedResult = adoptionApplicationRepository.findById(savedRejected.getId()).orElseThrow();
        assertEquals(AdoptionStatus.REJECTED, rejectedResult.getStatus());

        Cat unchangedCat = catRepository.findById(savedCat.getId()).orElseThrow();
        assertEquals(CatAdoptionStatus.ADOPTED, unchangedCat.getAdoptionStatus());
    }

}
