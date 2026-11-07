package com.dayo.capstone.cadence.common.event;

import java.util.List;
import java.util.UUID;

/** A course was published; these students can now open it. */
public class CoursePublishedEvent extends DomainEvent {

    private final List<Long> studentIds;
    private final String courseTitle;
    private final UUID coursePublicId;

    public CoursePublishedEvent(Long institutionId, Long actorId, List<Long> studentIds, String courseTitle, UUID coursePublicId) {
        super(institutionId, actorId);
        this.studentIds = List.copyOf(studentIds);
        this.courseTitle = courseTitle;
        this.coursePublicId = coursePublicId;
    }

    public List<Long> studentIds() { return studentIds; }
    public String courseTitle() { return courseTitle; }
    public UUID coursePublicId() { return coursePublicId; }
}
