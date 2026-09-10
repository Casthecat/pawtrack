package com.pawtrack.backend.identity.security;

import com.pawtrack.backend.identity.domain.UserRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.List;

// Detached, serializable identity snapshot. User erases its password after authentication.
public final class AccountPrincipal extends User {
    private final Long accountId;
    private final String displayName;
    private final UserRole role;

    public AccountPrincipal(Long accountId, String email, String displayName, String passwordHash, UserRole role) {
        super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        this.accountId = accountId;
        this.displayName = displayName;
        this.role = role;
    }

    public Long getAccountId() { return accountId; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
}
