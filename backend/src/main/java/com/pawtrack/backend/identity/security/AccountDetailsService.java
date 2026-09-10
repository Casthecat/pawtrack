package com.pawtrack.backend.identity.security;

import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDetailsService implements UserDetailsService {
    private final UserAccountRepository accounts;
    public AccountDetailsService(UserAccountRepository accounts) { this.accounts = accounts; }

    @Override @Transactional(readOnly = true)
    public AccountPrincipal loadUserByUsername(String email) {
        var account = accounts.findByEmail(IdentityService.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
        return new AccountPrincipal(account.getId(), account.getEmail(), account.getDisplayName(),
                account.getPasswordHash(), account.getRole());
    }
}
