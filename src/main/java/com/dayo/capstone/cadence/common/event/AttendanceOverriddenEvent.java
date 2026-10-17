package com.dayo.capstone.cadence.common.event;

import java.time.LocalDate;

/** A facilitator or admin set a student's attendance by manual override (class session or campus resumption). */
public class AttendanceOverriddenEvent extends DomainEvent {

    public enum Kind { CLASS, RESUMPTION }

    private final Long studentId;
    private final Kind kind;
    private final String what;      // the subject name, or "Campus resumption"
    private final LocalDate date;
    private final String status;    // PRESENT / LATE / ABSENT

    public AttendanceOverriddenEvent(Long institutionId, Long actorId, Long studentId,
                                     Kind kind, String what, LocalDate date, String status) {
        super(institutionId, actorId);
        this.studentId = studentId;
        this.kind = kind;
        this.what = what;
        this.date = date;
        this.status = status;
    }

    public Long studentId() { return studentId; }
    public Kind kind() { return kind; }
    public String what() { return what; }
    public LocalDate date() { return date; }
    public String status() { return status; }
}
