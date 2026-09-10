package com.pawtrack.backend.care.service;

import com.pawtrack.backend.support.TestAccounts;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.service.AdoptionService;
import com.pawtrack.backend.care.api.dto.HealthTimelineEventKind;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.main.web-application-type=none")
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoCareTimelineIntegrationTest {

    @Autowired UserAccountRepository accounts;
    @Autowired CatRepository cats;
    @Autowired HealthTimelineService timelines;
    @Autowired AdoptionService adoptions;

    @Test
    void demoSeedsSmallNoriTimeline_andPreservesP1ApplicationSubmission() {
        assertEquals(6, cats.count());
        Cat nori = findCat("Nori");
        var timeline = timelines.getTimeline(nori.getId());

        assertEquals(4, timeline.events().size());
        assertEquals(2, timeline.events().stream()
                .filter(event -> event.eventKind() == HealthTimelineEventKind.HEALTH_OBSERVATION)
                .count());
        assertEquals(1, timeline.events().stream()
                .filter(event -> event.eventKind() == HealthTimelineEventKind.ALERT)
                .count());
        assertEquals(1, timeline.events().stream()
                .filter(event -> event.eventKind() == HealthTimelineEventKind.CARE_RECORD)
                .count());
        assertEquals(CatHealthStatus.UNDER_OBSERVATION, nori.getHealthStatus());

        Cat mochi = findCat("Mochi");
        AdoptionApplicationRequest request = new AdoptionApplicationRequest();
        request.setCatId(mochi.getId());
        assertEquals("PENDING", adoptions.submitApplication(request, TestAccounts.adopter(accounts, "adopter@example.com")).status());
    }

    private Cat findCat(String name) {
        return cats.findAll().stream()
                .filter(cat -> name.equals(cat.getName()))
                .findFirst()
                .orElseThrow();
    }
}
