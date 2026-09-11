package com.pawtrack.backend.identity.config;

import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import javax.sql.DataSource;

@Configuration
@Profile("demo")
public class DemoAccounts {
    // Check before DataSource/JPA initialization: demo create-drop must never reach persistent data.
    @Bean
    static BeanFactoryPostProcessor disposableDemoOnly(Environment environment) {
        return factory -> {
            String url = environment.getProperty("spring.datasource.url", "");
            if (!url.matches("jdbc:h2:mem:[A-Za-z0-9_-]+(?:;DB_CLOSE_DELAY=-1)?")
                    || !"org.h2.Driver".equals(environment.getProperty("spring.datasource.driver-class-name"))
                    || !"create-drop".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
                throw new IllegalStateException("Demo requires a named in-memory H2 datasource and create-drop; refusing known-password provisioning.");
            }
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CommandLineRunner seedDemoAccounts(IdentityService identity, UserAccountRepository accounts, DataSource datasource, Environment environment) {
        return args -> {
            // Also check the actual connection, not just a configuration label.
            try (var connection = datasource.getConnection()) {
                String expected = environment.getRequiredProperty("spring.datasource.url").split(";", 2)[0];
                if (!"H2".equals(connection.getMetaData().getDatabaseProductName())
                        || !expected.equals(connection.getMetaData().getURL())) {
                    throw new IllegalStateException("Demo datasource is not the configured disposable H2 database; refusing known-password provisioning.");
                }
            }
            DemoAccountFixtures.seed(identity, accounts);
        };
    }
}
