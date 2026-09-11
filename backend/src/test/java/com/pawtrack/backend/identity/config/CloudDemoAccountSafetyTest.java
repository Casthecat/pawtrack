package com.pawtrack.backend.identity.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudDemoAccountSafetyTest {
    private MockEnvironment safe() {
        var env = new MockEnvironment();
        env.setActiveProfiles("demo-cloud");
        return env.withProperty("pawtrack.demo.database-acknowledgement", CloudDemoAccounts.ACK)
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/pawtrack_portfolio_demo")
                .withProperty("spring.datasource.driver-class-name", "org.postgresql.Driver")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.flyway.enabled", "true");
    }

    @Test void explicitDisposableConfigurationIsAccepted() {
        assertDoesNotThrow(() -> CloudDemoAccounts.validateConfiguration(safe()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:mem:demo", "jdbc:postgresql://localhost:5432/pawtrack",
            "jdbc:postgresql://localhost:5432/pawtrack_portfolio_demo?currentSchema=real_data"})
    void unsafeDatasourceIsRejectedBeforeInitialization(String url) {
        var env = safe().withProperty("spring.datasource.url", url);
        new ApplicationContextRunner().withInitializer(context -> context.setEnvironment(env))
                .withUserConfiguration(CloudDemoAccounts.class).run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(context.getStartupFailure().getMessage().contains("refusing known-password provisioning"));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"pawtrack.demo.database-acknowledgement", "spring.jpa.hibernate.ddl-auto",
            "spring.flyway.enabled", "spring.datasource.driver-class-name"})
    void missingOrChangedSafetyConditionRefusesProvisioning(String property) {
        assertThrows(IllegalStateException.class, () -> CloudDemoAccounts.validateConfiguration(safe().withProperty(property, "false")));
    }

    @ParameterizedTest @ValueSource(strings = {"default", "production", "test", "demo"})
    void ordinaryProfilesDoNotRegisterCloudSeeder(String profile) {
        new ApplicationContextRunner().withUserConfiguration(CloudDemoAccounts.class)
                .withPropertyValues("spring.profiles.active=" + profile).run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("seedCloudDemoAccounts"));
                });
        var mixed = safe(); mixed.setActiveProfiles("demo-cloud", profile);
        assertThrows(IllegalStateException.class, () -> CloudDemoAccounts.validateConfiguration(mixed));
    }

    @Test void actualConnectionMustMatchBeforeAnyKnownAccountIsCreated() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost:5432/ordinary_database");
        var identity = mock(IdentityService.class); var accounts = mock(UserAccountRepository.class);
        var runner = new CloudDemoAccounts().seedCloudDemoAccounts(identity, accounts, source, safe());
        assertThrows(IllegalStateException.class, () -> runner.run());
        verifyNoInteractions(identity, accounts);
        verify(connection).close();
    }
}
