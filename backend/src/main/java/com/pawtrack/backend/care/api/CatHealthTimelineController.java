package com.pawtrack.backend.care.api;

import com.pawtrack.backend.care.api.dto.CatHealthTimelineResponse;
import com.pawtrack.backend.care.service.HealthTimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cats/{catId}/health-timeline")
@Tag(name = "Care Timeline", description = "Read-only cat health and care timeline APIs")
public class CatHealthTimelineController {

    private final HealthTimelineService healthTimelineService;

    public CatHealthTimelineController(HealthTimelineService healthTimelineService) {
        this.healthTimelineService = healthTimelineService;
    }

    @GetMapping
    @Operation(summary = "Get a cat's health and care timeline, newest first")
    public CatHealthTimelineResponse getTimeline(@PathVariable Long catId) {
        return healthTimelineService.getTimeline(catId);
    }
}
