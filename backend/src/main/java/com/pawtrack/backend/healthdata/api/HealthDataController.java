package com.pawtrack.backend.healthdata.api;

import com.pawtrack.backend.alert.api.dto.AlertResponse;
import com.pawtrack.backend.alert.api.mapper.AlertMapper;
import com.pawtrack.backend.alert.service.AlertService;
import com.pawtrack.backend.healthdata.api.dto.CreateHealthDataRequest;
import com.pawtrack.backend.healthdata.api.dto.HealthDataResponse;
import com.pawtrack.backend.healthdata.api.mapper.HealthDataMapper;
import com.pawtrack.backend.healthdata.service.HealthDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cats/{catId}")
@Tag(name = "Health Data", description = "Health data APIs")
public class HealthDataController {

    private final HealthDataService healthDataService;
    private final AlertService alertService;

    public HealthDataController(HealthDataService healthDataService, AlertService alertService) {
        this.healthDataService = healthDataService;
        this.alertService = alertService;
    }

    @PostMapping("/health")
    @Operation(summary = "Create health data")
    public HealthDataResponse createHealthData(
            @PathVariable Long catId,
            @Valid @RequestBody CreateHealthDataRequest req
    ) {
        return HealthDataMapper.toResponse(
                healthDataService.create(catId, req.getTs(), req.getTemperatureC(), req.getActivityLevel())
        );
    }

    @GetMapping("/health")
    @Operation(summary = "List health data by cat")
    public List<HealthDataResponse> listHealthData(@PathVariable Long catId) {
        return healthDataService.listByCat(catId).stream()
                .map(HealthDataMapper::toResponse)
                .toList();
    }

    @GetMapping("/health/alerts")
    @Operation(summary = "List alerts by cat (health)")
    public List<AlertResponse> listAlerts(@PathVariable Long catId) {
        return alertService.listByCatId(catId).stream()
                .map(AlertMapper::toResponse)
                .toList();
    }
}
