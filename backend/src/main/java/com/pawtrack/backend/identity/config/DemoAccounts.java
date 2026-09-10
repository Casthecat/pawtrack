package com.pawtrack.backend.identity.config;

import com.pawtrack.backend.identity.domain.UserRole;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;

@Configuration
@Profile("demo")
public class DemoAccounts {
    @Bean
    CommandLineRunner seedDemoAccounts(IdentityService identity, UserAccountRepository accounts) {
        return args -> {
            if (accounts.findByEmail("staff@example.com").isEmpty())
                identity.createAccount("staff@example.com", "Demo Staff", "PawTrack-demo-staff!", UserRole.STAFF);
            if (accounts.findByEmail("adopter@example.com").isEmpty())
                identity.createAccount("adopter@example.com", "Demo Adopter", "PawTrack-demo-adopter!", UserRole.ADOPTER);
        };
    }
}
