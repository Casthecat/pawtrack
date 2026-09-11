package com.pawtrack.backend.identity.config;

import com.pawtrack.backend.identity.domain.UserRole;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;

// Called only after the local/cloud configuration has verified its disposable datasource.
final class DemoAccountFixtures {
    private DemoAccountFixtures() {}

    static void seed(IdentityService identity, UserAccountRepository accounts) {
        if (accounts.findByEmail("staff@example.com").isEmpty())
            identity.createAccount("staff@example.com", "Demo Staff", "PawTrack-demo-staff!", UserRole.STAFF);
        if (accounts.findByEmail("adopter@example.com").isEmpty())
            identity.createAccount("adopter@example.com", "Demo Adopter", "PawTrack-demo-adopter!", UserRole.ADOPTER);
        if (accounts.findByEmail("other-adopter@example.com").isEmpty())
            identity.createAccount("other-adopter@example.com", "Other Demo Adopter", "PawTrack-demo-other!", UserRole.ADOPTER);
    }
}
