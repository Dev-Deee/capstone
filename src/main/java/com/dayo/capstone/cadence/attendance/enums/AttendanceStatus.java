package com.dayo.capstone.cadence.attendance.enums;

// ABSENT is only ever stored by a manual override (a student with no record
// is also absent). How a record was produced lives in VerificationMethod.
public enum AttendanceStatus {
    PRESENT, LATE, ABSENT;

    public boolean countsAsAttended() {
        return this != ABSENT;
    }
}
