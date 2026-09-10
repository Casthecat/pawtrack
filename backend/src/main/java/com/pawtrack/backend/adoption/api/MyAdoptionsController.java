package com.pawtrack.backend.adoption.api;

import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationResponse;
import com.pawtrack.backend.adoption.service.AdoptionService;
import com.pawtrack.backend.identity.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class MyAdoptionsController {
    private final AdoptionService adoptions;

    @GetMapping("/api/me/adoptions")
    public List<AdoptionApplicationResponse> mine(@AuthenticationPrincipal AccountPrincipal principal) {
        return adoptions.mine(principal);
    }
}
