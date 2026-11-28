package com.dayo.capstone.cadence.messaging.repository;

import com.dayo.capstone.cadence.messaging.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c WHERE c.institution.id = :institutionId AND c.participantA.id = :aId AND c.participantB.id = :bId")
    Optional<Conversation> findPair(@Param("institutionId") Long institutionId, @Param("aId") Long aId, @Param("bId") Long bId);

    // Tenant and participation are part of the lookup: anything else is "not found".
    @Query("SELECT c FROM Conversation c WHERE c.publicId = :publicId AND c.institution.id = :institutionId "
            + "AND (c.participantA.id = :userId OR c.participantB.id = :userId)")
    Optional<Conversation> findMine(@Param("publicId") UUID publicId, @Param("institutionId") Long institutionId, @Param("userId") Long userId);

    // My conversations here that have at least one message, most recent first.
    @Query("SELECT c FROM Conversation c JOIN FETCH c.participantA JOIN FETCH c.participantB "
            + "WHERE c.institution.id = :institutionId AND (c.participantA.id = :userId OR c.participantB.id = :userId) "
            + "AND c.lastMessageAt IS NOT NULL ORDER BY c.lastMessageAt DESC")
    List<Conversation> findMyConversations(@Param("institutionId") Long institutionId, @Param("userId") Long userId);

    // ── Who may message whom (used by MessagingPolicy) ──

    // Students actively enrolled in an active cohort this facilitator actively teaches, here.
    @Query("SELECT DISTINCT e.student.id FROM CohortEnrollment e, FacilitatorCohort fc "
            + "WHERE fc.cohort = e.cohort AND fc.facilitator.id = :facilitatorId AND fc.isActive = true "
            + "AND e.isActive = true AND e.cohort.isActive = true AND e.cohort.institution.id = :institutionId")
    List<Long> findStudentIdsTaughtBy(@Param("institutionId") Long institutionId, @Param("facilitatorId") Long facilitatorId);

    // Facilitators actively teaching an active cohort this student is actively enrolled in, here.
    @Query("SELECT DISTINCT fc.facilitator.id FROM CohortEnrollment e, FacilitatorCohort fc "
            + "WHERE fc.cohort = e.cohort AND e.student.id = :studentId AND fc.isActive = true "
            + "AND e.isActive = true AND e.cohort.isActive = true AND e.cohort.institution.id = :institutionId")
    List<Long> findFacilitatorIdsTeaching(@Param("institutionId") Long institutionId, @Param("studentId") Long studentId);

    @Query("SELECT COUNT(e) FROM CohortEnrollment e, FacilitatorCohort fc "
            + "WHERE fc.cohort = e.cohort AND e.student.id = :studentId AND fc.facilitator.id = :facilitatorId "
            + "AND fc.isActive = true AND e.isActive = true AND e.cohort.isActive = true AND e.cohort.institution.id = :institutionId")
    long countTeaching(@Param("institutionId") Long institutionId, @Param("facilitatorId") Long facilitatorId, @Param("studentId") Long studentId);

    // Active, undeleted members here (other than me) whose name matches, with roles loaded.
    @Query("SELECT m FROM Membership m JOIN FETCH m.user u "
            + "WHERE m.institution.id = :institutionId AND m.isActive = true AND u.isDeleted = false AND u.isActive = true AND u.id <> :userId "
            + "AND LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE :pattern "
            + "ORDER BY u.firstName, u.lastName")
    List<com.dayo.capstone.cadence.institution.model.Membership> findMembersMatching(
            @Param("institutionId") Long institutionId, @Param("userId") Long userId, @Param("pattern") String pattern);
}
