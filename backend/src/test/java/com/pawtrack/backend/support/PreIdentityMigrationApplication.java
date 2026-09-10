package com.pawtrack.backend.support;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Preserve historical V7/V8 validation without requiring the not-yet-existing V9 identity table.
@TestConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = {"com.pawtrack.backend.cat",
        "com.pawtrack.backend.alert", "com.pawtrack.backend.healthdata", "com.pawtrack.backend.care"})
@EnableJpaRepositories(basePackages = {"com.pawtrack.backend.cat",
        "com.pawtrack.backend.alert", "com.pawtrack.backend.healthdata", "com.pawtrack.backend.care"})
public class PreIdentityMigrationApplication {}
