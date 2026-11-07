package com.dayo.capstone.cadence.course.enums;

// A DRAFT course is visible only to the people who can edit it; a DRAFT with
// publishAt set goes out on its own (CoursePublishJob).
public enum CourseStatus {
    DRAFT,
    PUBLISHED
}
