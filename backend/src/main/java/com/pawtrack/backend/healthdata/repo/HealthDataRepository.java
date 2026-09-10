package com.pawtrack.backend.healthdata.repo;

import com.pawtrack.backend.healthdata.domain.HealthData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HealthDataRepository extends JpaRepository<HealthData, Long> {
    // Later observation IDs win when recorded timestamps tie, including latest reads.
    List<HealthData> findByCatIdOrderByTsDescIdDesc(Long catId);
    Optional<HealthData> findFirstByCatIdOrderByTsDescIdDesc(Long catId);
}
