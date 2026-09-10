package com.pawtrack.backend.identity.api;

import com.pawtrack.backend.identity.domain.UserRole;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo-accounts;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo") @DirtiesContext
class DemoAccountsIntegrationTest {
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;

    @Test void demoContainsExactlyTwoEncodedAccounts() {
        assertEquals(2, accounts.count());
        var staff = accounts.findByEmail("staff@example.com").orElseThrow();
        var adopter = accounts.findByEmail("adopter@example.com").orElseThrow();
        assertEquals(UserRole.STAFF, staff.getRole());
        assertEquals(UserRole.ADOPTER, adopter.getRole());
        assertTrue(passwords.matches("PawTrack-demo-staff!", staff.getPasswordHash()));
        assertTrue(passwords.matches("PawTrack-demo-adopter!", adopter.getPasswordHash()));
        assertTrue(staff.getPasswordHash().startsWith("{bcrypt}"));
        assertTrue(adopter.getPasswordHash().startsWith("{bcrypt}"));
    }
}
