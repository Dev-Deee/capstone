package com.dayo.capstone.cadence.common.event;

import java.util.List;
import java.util.UUID;

/** An announcement was posted to cohorts; these students are actively enrolled in them. */
public class AnnouncementPostedEvent extends DomainEvent {

    private final List<Long> studentIds;
    private final String title;
    private final String authorName;
    private final UUID announcementPublicId;

    public AnnouncementPostedEvent(Long institutionId, Long actorId, List<Long> studentIds, String title,
                                   String authorName, UUID announcementPublicId) {
        super(institutionId, actorId);
        this.studentIds = List.copyOf(studentIds);
        this.title = title;
        this.authorName = authorName;
        this.announcementPublicId = announcementPublicId;
    }

    public List<Long> studentIds() { return studentIds; }
    public String title() { return title; }
    public String authorName() { return authorName; }
    public UUID announcementPublicId() { return announcementPublicId; }
}
