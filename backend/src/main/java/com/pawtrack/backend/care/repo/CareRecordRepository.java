package com.pawtrack.backend.care.repo;

import com.pawtrack.backend.care.domain.CareRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareRecordRepository extends JpaRepository<CareRecord, Long> {
    List<CareRecord> findByCatIdOrderByCreatedAtDescIdDesc(Long catId);
}
