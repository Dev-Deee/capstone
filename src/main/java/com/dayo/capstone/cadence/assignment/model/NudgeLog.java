package com.dayo.capstone.cadence.assignment.model;

import com.dayo.capstone.cadence.assignment.enums.NudgeKind;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One automatic reminder that has been sent. The unique constraint is the
 * guard against sending it twice: AssignmentNudgeJob claims a row before it
 * notifies anyone, so reruns, restarts and several app instances can't
 * repeat a reminder. Rows are written by NudgeLogRepository.claim, not by
 * JPA saves.
 *
 * targetKey says what the reminder was about: an assignment's publicId, a
 * submission's publicId plus its redo round, or a day plus an institution.
 */
@Entity
@Table(name = "nudge_log", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nudge_log_once", columnNames = {"kind", "recipient_id", "target_key"})
})
@Getter
@Setter
@NoArgsConstructor
public class NudgeLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NudgeKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "target_key", nullable = false, length = 100)
    private String targetKey;
}
