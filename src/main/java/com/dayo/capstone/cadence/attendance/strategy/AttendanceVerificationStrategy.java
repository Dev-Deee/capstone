package com.dayo.capstone.cadence.attendance.strategy;

import com.dayo.capstone.cadence.attendance.model.Session;

/**
 * PRD Section 6.1 — strategy pattern for verification. Only
 * SessionCodeStrategy is implemented in this pass; QR and COMBINED
 * modes implement this same interface when they're layered in later,
 * with no changes needed to AttendanceRecordService's business logic.
 */
public interface AttendanceVerificationStrategy {
    boolean verify(Session session, String submittedValue);
}