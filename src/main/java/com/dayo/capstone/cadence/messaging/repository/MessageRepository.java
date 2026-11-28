package com.dayo.capstone.cadence.messaging.repository;

import com.dayo.capstone.cadence.messaging.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.publicId = :publicId AND m.conversation.id = :conversationId")
    Optional<Message> findInConversation(@Param("publicId") UUID publicId, @Param("conversationId") Long conversationId);

    // Newest first; the service reverses each page so the thread reads oldest → newest.
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.conversation.id = :conversationId ORDER BY m.id DESC")
    List<Message> findLatest(@Param("conversationId") Long conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.conversation.id = :conversationId AND m.id < :beforeId ORDER BY m.id DESC")
    List<Message> findBefore(@Param("conversationId") Long conversationId, @Param("beforeId") Long beforeId, Pageable pageable);

    // Polling: what arrived since the newest message the client already has.
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.conversation.id = :conversationId AND m.id > :afterId ORDER BY m.id ASC")
    List<Message> findAfter(@Param("conversationId") Long conversationId, @Param("afterId") Long afterId, Pageable pageable);

    @Query("SELECT MAX(m.id) FROM Message m WHERE m.conversation.id = :conversationId")
    Long findLatestId(@Param("conversationId") Long conversationId);

    // Unread = from the other person, above my read watermark. As [conversationId, count] rows.
    @Query("SELECT m.conversation.id, COUNT(m) FROM Message m JOIN m.conversation c "
            + "WHERE c.institution.id = :institutionId AND m.sender.id <> :userId AND ("
            + "  (c.participantA.id = :userId AND (c.aLastReadMessageId IS NULL OR m.id > c.aLastReadMessageId)) OR "
            + "  (c.participantB.id = :userId AND (c.bLastReadMessageId IS NULL OR m.id > c.bLastReadMessageId))) "
            + "GROUP BY m.conversation.id")
    List<Object[]> countUnreadByConversation(@Param("institutionId") Long institutionId, @Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM Message m JOIN m.conversation c "
            + "WHERE c.institution.id = :institutionId AND m.sender.id <> :userId AND ("
            + "  (c.participantA.id = :userId AND (c.aLastReadMessageId IS NULL OR m.id > c.aLastReadMessageId)) OR "
            + "  (c.participantB.id = :userId AND (c.bLastReadMessageId IS NULL OR m.id > c.bLastReadMessageId)))")
    long countUnread(@Param("institutionId") Long institutionId, @Param("userId") Long userId);
}
