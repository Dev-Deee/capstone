package com.dayo.capstone.cadence.attendance.enums;

// ABSENT is only ever stored by a manual override (a student with no record
// is also absent). How a record was produced lives in VerificationMethod.
// PENDING_BIOMETRIC: the code was accepted for a webAuthnCapable student, who
// has not yet confirmed with fingerprint / Face ID (PRD Section 6.12). It is
// not attendance — it completes on a verified assertion, or a facilitator
// resolves it through the manual override.
public enum AttendanceStatus {
    PRESENT, LATE, ABSENT, PENDING_BIOMETRIC;

    public boolean countsAsAttended() {
        return this == PRESENT || this == LATE;
    }

    /** The statuses a facilitator may set by manual override. */
    public boolean isOverrideOutcome() {
        return this != PENDING_BIOMETRIC;
    }
}
