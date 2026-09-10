package com.pawtrack.backend.healthdata.repo;

import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.domain.HealthData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HealthDataRepositoryTest {

    private final CatRepository catRepository;
    private final HealthDataRepository healthDataRepository;

    @Autowired
    HealthDataRepositoryTest(CatRepository catRepository, HealthDataRepository healthDataRepository) {
        this.catRepository = catRepository;
        this.healthDataRepository = healthDataRepository;
    }

    @Test
    void save_generatesTsAndCreatedAt() {
        Cat cat = new Cat("TestCat");
        Cat savedCat = catRepository.save(cat);

        HealthData data = new HealthData();
        data.setCat(savedCat);
        HealthData saved = healthDataRepository.save(data);

        assertNotNull(saved.getTs());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void findFirstByCatIdOrderByTsDescIdDesc_returnsLatest() {
        Cat cat = new Cat("LatestCat");
        Cat savedCat = catRepository.save(cat);

        HealthData older = new HealthData();
        older.setCat(savedCat);
        older.setTs(OffsetDateTime.now().minusHours(2));
        HealthData savedOlder = healthDataRepository.save(older);

        HealthData latest = new HealthData();
        latest.setCat(savedCat);
        latest.setTs(OffsetDateTime.now().minusHours(1));
        HealthData savedLatest = healthDataRepository.save(latest);

        Optional<HealthData> result = healthDataRepository.findFirstByCatIdOrderByTsDescIdDesc(savedCat.getId());

        assertTrue(result.isPresent());
        assertEquals(savedLatest.getId(), result.get().getId());
        assertEquals(savedLatest.getTs(), result.get().getTs());
        assertEquals(savedOlder.getCat().getId(), result.get().getCat().getId());
    }
}
