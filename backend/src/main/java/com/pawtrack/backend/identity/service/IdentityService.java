package com.pawtrack.backend.identity.service;

import com.pawtrack.backend.identity.domain.UserAccount;
import com.pawtrack.backend.identity.domain.UserRole;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class IdentityService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Validator validator;

    public IdentityService(UserAccountRepository accounts, PasswordEncoder passwords, Validator validator) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.validator = validator;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    // Internal provisioning only; no public registration route in P4.1.
    @Transactional
    public UserAccount createAccount(String email, String displayName, String password, UserRole role) {
        if (password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be non-blank and at most 72 UTF-8 bytes.");
        }
        UserAccount account = new UserAccount(normalizeEmail(email),
                displayName == null ? "" : displayName.trim(), passwords.encode(password), role);
        var violations = validator.validate(account);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        // The database is the uniqueness authority, including concurrent provisioning.
        return accounts.saveAndFlush(account);
    }
}
