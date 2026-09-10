package com.pawtrack.backend.alert.repo;

import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.domain.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("select a from Alert a join fetch a.cat where a.status = :status order by a.createdAt desc, a.id desc")
    List<Alert> findQueueByStatus(@Param("status") AlertStatus status);

    @Query("select a.cat.id from Alert a where a.id = :id")
    Optional<Long> findCatIdByAlertId(@Param("id") Long id);

    @Query("select a from Alert a join fetch a.cat where a.id = :id")
    Optional<Alert> findWithCatById(@Param("id") Long id);

    // Fetch cat data with alerts for DTO mapping without OSIV or per-row queries.
    @Query("""
           select a
           from Alert a
           join fetch a.cat c
           where c.id = :catId
           order by a.createdAt desc
           """)
    List<Alert> findByCatIdWithCat(@Param("catId") Long catId);

    // Used to deduplicate open alerts for the same cat and type.
    @Query("""
           select a
           from Alert a
           where a.cat.id = :catId
             and a.type = :type
             and a.status = :status
           order by a.createdAt desc
           """)
    List<Alert> findLatestByCatAndTypeAndStatus(
            @Param("catId") Long catId,
            @Param("type") AlertType type,
            @Param("status") AlertStatus status
    );

    default Optional<Alert> findLatestOpenByCatAndType(Long catId, AlertType type) {
        List<Alert> list = findLatestByCatAndTypeAndStatus(catId, type, AlertStatus.OPEN);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    boolean existsByCatIdAndStatus(Long catId, AlertStatus status);
}
