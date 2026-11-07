package com.dayo.capstone.cadence.assignment.enums;

// How an assignment is graded, chosen when it is created and fixed once the
// first submission is graded, so grades within one assignment stay comparable.
public enum GradingType {
    MARKS,          // a score out of the assignment's maxMarks
    PERCENTAGE,     // 0–100
    PASS_FAIL,
    FEEDBACK_ONLY   // comments, no grade
}
