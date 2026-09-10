package com.pawtrack.backend.care.service;

import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.care.api.dto.CatHealthTimelineResponse;
import com.pawtrack.backend.care.api.dto.HealthTimelineEventKind;
import com.pawtrack.backend.care.api.dto.HealthTimelineEventResponse;
import com.pawtrack.backend.care.api.dto.HealthTimelineOrder;
import com.pawtrack.backend.care.api.mapper.HealthTimelineMapper;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.service.CatService;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class HealthTimelineService {

    private static final Comparator<HealthTimelineEventResponse> NEWEST_FIRST =
            Comparator.comparing(HealthTimelineEventResponse::occurredAt, Comparator.reverseOrder())
                    .thenComparingInt(event -> eventKindRank(event.eventKind()))
                    .thenComparing(HealthTimelineEventResponse::sourceId, Comparator.reverseOrder());

    private final CatService catService;
    private final HealthDataRepository healthDataRepository;
    private final AlertRepository alertRepository;
    private final CareRecordRepository careRecordRepository;

    public HealthTimelineService(
            CatService catService,
            HealthDataRepository healthDataRepository,
            AlertRepository alertRepository,
            CareRecordRepository careRecordRepository
    ) {
        this.catService = catService;
        this.healthDataRepository = healthDataRepository;
        this.alertRepository = alertRepository;
        this.careRecordRepository = careRecordRepository;
    }

    @Transactional(readOnly = true)
    public CatHealthTimelineResponse getTimeline(Long catId) {
        Cat cat = catService.getById(catId);
        List<HealthTimelineEventResponse> events = new ArrayList<>();

        healthDataRepository.findByCatIdOrderByTsDescIdDesc(catId).stream()
                .map(HealthTimelineMapper::fromObservation)
                .forEach(events::add);
        alertRepository.findByCatIdWithCat(catId).stream()
                .map(HealthTimelineMapper::fromAlert)
                .forEach(events::add);
        careRecordRepository.findByCatIdOrderByCreatedAtDescIdDesc(catId).stream()
                .map(HealthTimelineMapper::fromCareRecord)
                .forEach(events::add);

        events.sort(NEWEST_FIRST);
        return new CatHealthTimelineResponse(
                cat.getId(),
                cat.getName(),
                HealthTimelineOrder.NEWEST_FIRST,
                List.copyOf(events)
        );
    }

    private static int eventKindRank(HealthTimelineEventKind kind) {
        return switch (kind) {
            case ALERT -> 0;
            case CARE_RECORD -> 1;
            case HEALTH_OBSERVATION -> 2;
        };
    }
}
