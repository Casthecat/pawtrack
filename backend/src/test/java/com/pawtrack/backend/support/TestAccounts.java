package com.pawtrack.backend.support;

import com.pawtrack.backend.identity.domain.*;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.security.AccountPrincipal;
import com.pawtrack.backend.identity.service.IdentityService;

// Repository fixtures for business tests only. Ownership API tests log in through the real filter.
public final class TestAccounts {
    private TestAccounts() {}
    public static AccountPrincipal adopter(UserAccountRepository accounts, String email) {
        String normalized = IdentityService.normalizeEmail(email);
        var account = accounts.findByEmail(normalized).orElseGet(() -> accounts.saveAndFlush(
                new UserAccount(normalized, "Alex", "{noop}fixture-only", UserRole.ADOPTER)));
        return principal(account);
    }
    public static AccountPrincipal principal(UserAccount account) {
        return new AccountPrincipal(account.getId(), account.getEmail(), account.getDisplayName(), account.getPasswordHash(), account.getRole());
    }
    public static AccountPrincipal staff() {
        return new AccountPrincipal(-1L, "regression-staff@example.com", "Staff", "unused", UserRole.STAFF);
    }
}
