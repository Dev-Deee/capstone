package com.dayo.capstone.cadence.notification.repository;

import com.dayo.capstone.cadence.notification.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    @Query("SELECT s FROM PushSubscription s WHERE s.user.id = :userId ORDER BY s.createdAt ASC, s.id ASC")
    List<PushSubscription> findByUserOldestFirst(@Param("userId") Long userId);

    @Query("SELECT s FROM PushSubscription s JOIN FETCH s.user WHERE s.user.id IN :userIds")
    List<PushSubscription> findForUsers(@Param("userIds") Collection<Long> userIds);

    // Ownership is part of the delete: someone else's endpoint is left alone.
    @Transactional
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.endpoint = :endpoint AND s.user.id = :userId")
    int deleteOwned(@Param("endpoint") String endpoint, @Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("UPDATE PushSubscription s SET s.lastSuccessAt = :now WHERE s.id = :id")
    int markSuccess(@Param("id") Long id, @Param("now") LocalDateTime now);
}
