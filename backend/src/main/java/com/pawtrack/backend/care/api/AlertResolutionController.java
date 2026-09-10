package com.pawtrack.backend.care.api;

import com.pawtrack.backend.care.api.dto.AlertResolutionRequest;
import com.pawtrack.backend.care.api.dto.AlertResolutionResponse;
import com.pawtrack.backend.care.service.AlertResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertResolutionController {
    private final AlertResolutionService resolutionService;

    @PatchMapping("/{alertId}/resolve")
    @Operation(summary = "Resolve an open alert and record the staff care action")
    public AlertResolutionResponse resolve(
            @PathVariable Long alertId,
            @Valid @RequestBody AlertResolutionRequest request
    ) {
        return resolutionService.resolve(alertId, request);
    }
}
