package com.pawtrack.backend.care.api.dto;

import java.util.List;

public record CatHealthTimelineResponse(
        Long catId,
        String catName,
        HealthTimelineOrder order,
        List<HealthTimelineEventResponse> events
) {
}
