package com.pawtrack.backend.identity.api;

import com.pawtrack.backend.identity.api.dto.CurrentUserResponse;
import com.pawtrack.backend.identity.security.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return CurrentUserResponse.from(principal);
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        return new CsrfResponse(csrf.getHeaderName(), csrf.getToken());
    }

    public record CsrfResponse(String headerName, String token) {}
}
