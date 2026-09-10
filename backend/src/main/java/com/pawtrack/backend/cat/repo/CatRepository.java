package com.pawtrack.backend.cat.repo;

import com.pawtrack.backend.cat.domain.Cat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CatRepository extends JpaRepository<Cat, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cat c where c.id = :id")
    Optional<Cat> findByIdForUpdate(@Param("id") Long id);
}
