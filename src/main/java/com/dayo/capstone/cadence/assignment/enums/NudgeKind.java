package com.dayo.capstone.cadence.assignment.enums;

// The automatic reminders AssignmentNudgeJob sends. Each goes to a person at
// most once per target (see NudgeLog).
public enum NudgeKind {
    ASSIGNMENT_DUE_SOON,    // target: the assignment
    ASSIGNMENT_OVERDUE,     // target: the assignment
    REDO_DUE_SOON,          // target: the submission and its redo round
    REVIEW_WAITING_DIGEST   // target: the day and institution
}
