package com.dayo.capstone.cadence.audit.service;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.model.AuditLog;
import com.dayo.capstone.cadence.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Called by any module needing to record a privileged action — never
 * bypassed for anything Section 9 names explicitly (overrides,
 * approvals/denials, consent changes, disputes, exports, deletions, logins).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(UUID actorPublicId, String actorRole, AuditAction action,
                    String entityType, UUID entityPublicId,
                    String beforeState, String afterState) {
        AuditLog entry = new AuditLog();
        entry.setActorPublicId(actorPublicId != null ? actorPublicId.toString() : null);
        entry.setActorRole(actorRole);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityPublicId(entityPublicId != null ? entityPublicId.toString() : null);
        entry.setBeforeState(beforeState);
        entry.setAfterState(afterState);

        auditLogRepository.save(entry);
        log.debug("AuditLog recorded: action={}, entityType={}, entityPublicId={}",
                action, entityType, entityPublicId);
    }

    // Convenience overload for actions with no before/after state (logins, logouts).
    public void log(UUID actorPublicId, String actorRole, AuditAction action, String entityType, UUID entityPublicId) {
        log(actorPublicId, actorRole, action, entityType, entityPublicId, null, null);
    }
}