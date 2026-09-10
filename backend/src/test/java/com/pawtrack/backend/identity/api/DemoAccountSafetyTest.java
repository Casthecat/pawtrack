package com.pawtrack.backend.identity.api;

import com.pawtrack.backend.BackendApplication;
import com.pawtrack.backend.identity.config.DemoAccounts;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoAccountSafetyTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(strings = {"default", "test", "postgres-migration", "postgres-runtime"})
    void nonDemoProfilesNeverActivateKnownPasswordProvisioning(String profile) {
        var identity = mock(IdentityService.class);
        var accounts = mock(UserAccountRepository.class);
        new ApplicationContextRunner().withUserConfiguration(DemoAccounts.class)
                .withPropertyValues("spring.profiles.active=" + profile)
                .withBean(IdentityService.class, () -> identity).withBean(UserAccountRepository.class, () -> accounts)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("seedDemoAccounts"));
                    verifyNoInteractions(identity, accounts);
                });
    }

    @Test void unsafeDemoDatasourceFailsBeforeJpaAndLeavesIsolatedPersistentDatabaseUntouched() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("persistent-fixture").toString().replace('\\', '/');
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.execute("create table user_accounts(email varchar(200))");
            sql.execute("insert into user_accounts values ('existing@example.com')");
        }
        var failure = assertThrows(Exception.class, () -> {
            try (var ignored = new SpringApplicationBuilder(BackendApplication.class).web(WebApplicationType.NONE)
                    .profiles("demo").run("--spring.datasource.url=" + url, "--spring.datasource.driver-class-name=org.h2.Driver",
                            "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.flyway.enabled=false",
                            "--spring.jpa.hibernate.ddl-auto=create-drop", "--logging.level.root=ERROR")) {
                fail("Unsafe demo configuration must not start");
            }
        });
        assertTrue(rootCause(failure).getMessage().contains("refusing known-password provisioning"));
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement();
             var rows = sql.executeQuery("select email from user_accounts")) {
            assertTrue(rows.next()); assertEquals("existing@example.com", rows.getString(1)); assertFalse(rows.next());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"jdbc:postgresql://127.0.0.1:1/disposable-probe", "jdbc:h2:mem:demo;INIT=RUNSCRIPT FROM 'untrusted'"})
    void unsafeUrlIsRejectedWithoutCreatingADatasource(String url) {
        new ApplicationContextRunner().withUserConfiguration(DemoAccounts.class)
                .withPropertyValues("spring.profiles.active=demo", "spring.datasource.url=" + url,
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(rootCause(context.getStartupFailure()).getMessage().contains("refusing known-password provisioning"));
                });
    }

    private static Throwable rootCause(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error;
    }
}
