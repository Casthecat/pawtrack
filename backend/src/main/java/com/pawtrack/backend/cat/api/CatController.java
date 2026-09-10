package com.pawtrack.backend.cat.api;

import com.pawtrack.backend.alert.api.dto.AlertResponse;
import com.pawtrack.backend.alert.api.mapper.AlertMapper;
import com.pawtrack.backend.alert.service.AlertService;
import com.pawtrack.backend.cat.api.dto.CatCreateRequest;
import com.pawtrack.backend.cat.api.dto.CatDetailResponse;
import com.pawtrack.backend.cat.api.dto.CatResponse;
import com.pawtrack.backend.cat.api.dto.UpdateCatStatusRequest;
import com.pawtrack.backend.cat.api.mapper.CatMapper;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.service.CatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/cats")
@Tag(name = "Cats", description = "Cat management APIs")
public class CatController {

    private final CatService catService;
    private final AlertService alertService;

    public CatController(CatService catService, AlertService alertService) {
        this.catService = catService;
        this.alertService = alertService;
    }

    @PostMapping
    @Operation(summary = "Create cat")
    public CatResponse create(@jakarta.validation.Valid @RequestBody CatCreateRequest req) {
        Cat saved = catService.create(req.getName());
        return CatMapper.toResponse(saved);
    }

    @GetMapping
    @Operation(summary = "List cats")
    public List<CatResponse> list() {
        return catService.list().stream()
                .map(CatMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cat by id")
    public CatResponse getById(@PathVariable Long id) {
        return CatMapper.toResponse(catService.getById(id));
    }

    @GetMapping("/{id}/dashboard")
    @Operation(summary = "Get cat dashboard data")
    public CatDetailResponse getDashboard(@PathVariable Long id) {
        return catService.getDashboard(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update cat status")
    public CatResponse updateStatus(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody UpdateCatStatusRequest req
    ) {
        return CatMapper.toResponse(catService.updateStatus(id, req.getStatus()));
    }

    @PostMapping("/{id}/upload-image")
    @Operation(summary = "Upload cat image")
    public CatResponse uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        return CatMapper.toResponse(catService.uploadCatImage(id, file));
    }

    @GetMapping("/{catId}/alerts")
    @Operation(summary = "List alerts by cat")
    public List<AlertResponse> listAlerts(@PathVariable Long catId) {
        return alertService.listByCatId(catId).stream()
                .map(AlertMapper::toResponse)
                .toList();
    }
}
