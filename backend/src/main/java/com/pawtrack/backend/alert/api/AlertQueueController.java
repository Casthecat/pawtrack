package com.pawtrack.backend.alert.api;

import com.pawtrack.backend.alert.api.dto.AlertResponse;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertQueueController {
    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "List care alerts by status, newest first; defaults to OPEN")
    public List<AlertResponse> list(@RequestParam(defaultValue = "OPEN") AlertStatus status) {
        return alertService.list(status);
    }
}
