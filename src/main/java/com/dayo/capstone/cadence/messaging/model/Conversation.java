package com.dayo.capstone.cadence.messaging.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A 1:1 conversation between two members of one institution. There is at most
 * one per pair per institution: participantA is always the user with the
 * lower internal id, so the pair has a single canonical order and the unique
 * constraint catches duplicates.
 *
 * Read state is a per-participant watermark: the internal id of the last
 * message that participant has read. Messages from the other person with a
 * higher id are unread. Ids only ever grow, so this never misses a message
 * sent in the same millisecond as a read (a timestamp could).
 */
@Entity
@Table(name = "conversation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"institution_id", "participant_a_id", "participant_b_id"}),
        indexes = {
                @Index(name = "idx_conversation_a", columnList = "institution_id, participant_a_id, last_message_at"),
                @Index(name = "idx_conversation_b", columnList = "institution_id, participant_b_id, last_message_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_a_id", nullable = false)
    private User participantA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_b_id", nullable = false)
    private User participantB;

    // Denormalised from the newest message so the conversation list is one query.
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "last_message_sender_id")
    private Long lastMessageSenderId;

    @Column(name = "a_last_read_message_id")
    private Long aLastReadMessageId;

    @Column(name = "b_last_read_message_id")
    private Long bLastReadMessageId;

    @Column(name = "a_last_read_at")
    private LocalDateTime aLastReadAt;

    @Column(name = "b_last_read_at")
    private LocalDateTime bLastReadAt;

    public boolean involves(User user) {
        return participantA.getId().equals(user.getId()) || participantB.getId().equals(user.getId());
    }

    public User other(User me) {
        return participantA.getId().equals(me.getId()) ? participantB : participantA;
    }

    public void markReadBy(User user, Long messageId, LocalDateTime at) {
        if (participantA.getId().equals(user.getId())) {
            if (aLastReadMessageId == null || (messageId != null && messageId > aLastReadMessageId)) aLastReadMessageId = messageId;
            aLastReadAt = at;
        } else {
            if (bLastReadMessageId == null || (messageId != null && messageId > bLastReadMessageId)) bLastReadMessageId = messageId;
            bLastReadAt = at;
        }
    }
}
