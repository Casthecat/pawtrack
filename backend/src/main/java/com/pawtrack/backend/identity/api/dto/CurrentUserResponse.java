package com.pawtrack.backend.identity.api.dto;

import com.pawtrack.backend.identity.domain.UserRole;
import com.pawtrack.backend.identity.security.AccountPrincipal;

public record CurrentUserResponse(Long id, String email, String displayName, UserRole role) {
    public static CurrentUserResponse from(AccountPrincipal principal) {
        return new CurrentUserResponse(principal.getAccountId(), principal.getUsername(),
                principal.getDisplayName(), principal.getRole());
    }
}
