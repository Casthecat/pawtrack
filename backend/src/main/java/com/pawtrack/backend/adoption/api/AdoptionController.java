package com.pawtrack.backend.adoption.api;

import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationResponse;
import com.pawtrack.backend.adoption.domain.AdoptionStatus;
import com.pawtrack.backend.adoption.service.AdoptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.pawtrack.backend.identity.security.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/adoptions")
@RequiredArgsConstructor
@Tag(name = "Adoptions", description = "Account-owned adopter applications and staff review")
public class AdoptionController {
    private final AdoptionService adoptionService;

    @PostMapping
    @Operation(summary = "Submit adoption application")
    public ResponseEntity<AdoptionApplicationResponse> submit(@Valid @RequestBody AdoptionApplicationRequest req,
            @AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adoptionService.submitApplication(req, principal));
    }

    @GetMapping
    @Operation(summary = "List applications for review")
    public List<AdoptionApplicationResponse> list(@RequestParam(required = false) AdoptionStatus status) {
        return adoptionService.list(status);
    }

    @GetMapping("/{id}")
    public AdoptionApplicationResponse getById(@PathVariable Long id, @AuthenticationPrincipal AccountPrincipal principal) {
        return adoptionService.getById(id, principal);
    }

    @PatchMapping("/{id}/approve")
    public AdoptionApplicationResponse approve(@PathVariable Long id) {
        return adoptionService.approveApplication(id);
    }

    @PatchMapping("/{id}/reject")
    public AdoptionApplicationResponse reject(@PathVariable Long id) {
        return adoptionService.rejectApplication(id);
    }
}
