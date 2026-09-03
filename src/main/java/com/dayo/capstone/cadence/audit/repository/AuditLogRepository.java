package com.dayo.capstone.cadence.audit.repository;

import com.dayo.capstone.cadence.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}