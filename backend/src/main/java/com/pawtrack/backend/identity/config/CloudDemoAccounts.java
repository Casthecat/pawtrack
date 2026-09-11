package com.pawtrack.backend.identity.config;

import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.service.IdentityService;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.util.Arrays;

@Configuration
@Profile("demo-cloud")
public class CloudDemoAccounts {
    static final String ACK = "I_ACKNOWLEDGE_THIS_DATABASE_IS_DISPOSABLE";
    private static final String DATABASE = "pawtrack_portfolio_demo(?:_[a-f0-9]{32})?";
    private static final String REFUSAL = "Unsafe demo-cloud configuration; refusing known-password provisioning.";

    @Bean
    static BeanFactoryPostProcessor disposableCloudDemoOnly(Environment environment) {
        // Run before Flyway/JPA can modify a misconfigured datasource.
        return factory -> validateConfiguration(environment);
    }

    static void validateConfiguration(Environment environment) {
        try {
            String url = environment.getRequiredProperty("spring.datasource.url");
            URI uri = URI.create(url.substring("jdbc:".length()));
            if (!Arrays.equals(environment.getActiveProfiles(), new String[]{"demo-cloud"})
                    || !ACK.equals(environment.getProperty("pawtrack.demo.database-acknowledgement"))
                    || !url.startsWith("jdbc:postgresql://") || !"postgresql".equals(uri.getScheme())
                    || uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getPath().matches("/" + DATABASE)
                    || !"org.postgresql.Driver".equals(environment.getProperty("spring.datasource.driver-class-name"))
                    || !"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                    || !environment.getProperty("spring.flyway.enabled", Boolean.class, false)
                    || !environment.getProperty("spring.flyway.clean-disabled", Boolean.class, true)) {
                throw new IllegalStateException(REFUSAL);
            }
        } catch (RuntimeException failure) {
            // Do not include connection properties/passwords in the failure message.
            throw new IllegalStateException(REFUSAL);
        }
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CommandLineRunner seedCloudDemoAccounts(IdentityService identity, UserAccountRepository accounts,
                                          DataSource datasource, Environment environment) {
        return args -> {
            validateConfiguration(environment);
            try (var connection = datasource.getConnection()) {
                if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())
                        || !environment.getRequiredProperty("spring.datasource.url").equals(connection.getMetaData().getURL())
                        || connection.getCatalog() == null || !connection.getCatalog().matches(DATABASE)) {
                    throw new IllegalStateException(REFUSAL);
                }
            }
            DemoAccountFixtures.seed(identity, accounts);
        };
    }
}
