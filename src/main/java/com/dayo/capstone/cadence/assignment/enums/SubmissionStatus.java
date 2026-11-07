package com.dayo.capstone.cadence.assignment.enums;

public enum SubmissionStatus {
    PENDING,
    SUBMITTED,
    LATE_SUBMITTED,
    MISSING,
    REVIEWED,
    // Sent back by the facilitator: the student may submit again until redoDueAt.
    REDO_REQUESTED
}
