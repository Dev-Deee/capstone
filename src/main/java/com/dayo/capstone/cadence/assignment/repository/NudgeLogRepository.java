package com.dayo.capstone.cadence.assignment.repository;

import com.dayo.capstone.cadence.assignment.enums.NudgeKind;
import com.dayo.capstone.cadence.assignment.model.NudgeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface NudgeLogRepository extends JpaRepository<NudgeLog, Long> {

    // Records a reminder as sent. 1: this caller claimed it and should send it;
    // 0: it was already sent (the unique constraint turned the insert away).
    @Modifying
    @Query(value = """
            INSERT INTO nudge_log (public_id, created_at, kind, recipient_id, target_key)
            VALUES (:publicId, :now, :kind, :recipientId, :targetKey)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("publicId") UUID publicId, @Param("now") LocalDateTime now, @Param("kind") String kind,
              @Param("recipientId") Long recipientId, @Param("targetKey") String targetKey);

    long countByKindAndTargetKey(NudgeKind kind, String targetKey);
}
