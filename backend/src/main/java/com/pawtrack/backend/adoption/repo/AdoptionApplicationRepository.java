package com.pawtrack.backend.adoption.repo;

import com.pawtrack.backend.adoption.domain.AdoptionApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import com.pawtrack.backend.adoption.domain.AdoptionStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AdoptionApplicationRepository extends JpaRepository<AdoptionApplication, Long> {
    @Query("select a from AdoptionApplication a join fetch a.cat where (:status is null or a.status = :status) order by a.createdAt desc, a.id desc")
    List<AdoptionApplication> findForReview(@Param("status") AdoptionStatus status);

    @Query("select a from AdoptionApplication a join fetch a.cat where a.id = :id")
    Optional<AdoptionApplication> findWithCatById(@Param("id") Long id);

    @Query("select a.cat.id from AdoptionApplication a where a.id = :id")
    Optional<Long> findCatIdByApplicationId(@Param("id") Long id);

    @Query("select a from AdoptionApplication a join fetch a.cat where a.adopterAccount.id = :ownerId order by a.createdAt desc, a.id desc")
    List<AdoptionApplication> findForOwner(@Param("ownerId") Long ownerId);

    boolean existsByCatIdAndAdopterAccountIdAndStatus(Long catId, Long ownerId, AdoptionStatus status);
    List<AdoptionApplication> findByCatIdAndStatus(Long catId, AdoptionStatus status);
}
