package com.pawtrack.backend.config;

import com.pawtrack.backend.care.domain.CareRecord;
import com.pawtrack.backend.care.domain.CareRecordType;
import com.pawtrack.backend.care.repo.CareRecordRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.service.HealthDataService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Configuration
@Profile("demo")
public class DemoData {
    @Bean
    CommandLineRunner seedDemo(
            CatRepository cats,
            HealthDataService health,
            CareRecordRepository careRecords
    ) {
        return args -> {
            if (cats.count() != 0) return;
            cats.save(new Cat("Mochi"));
            cats.save(new Cat("Cleo"));
            cats.save(new Cat("Oliver"));
            Cat nori = cats.save(new Cat("Nori"));
            cats.save(new Cat("Maple"));
            cats.save(new Cat("Luna"));

            OffsetDateTime now = OffsetDateTime.now();
            health.create(nori.getId(), now.minusHours(6), new BigDecimal("38.3"), 6);
            health.create(nori.getId(), now.minusMinutes(45), new BigDecimal("40.0"), 1);

            CareRecord checkup = new CareRecord();
            checkup.setCat(nori);
            checkup.setType(CareRecordType.CHECKUP);
            checkup.setNote("Staff checked Nori, offered water, and moved her to a quiet observation area.");
            careRecords.save(checkup);
        };
    }
}
