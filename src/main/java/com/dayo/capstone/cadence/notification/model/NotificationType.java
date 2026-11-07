package com.dayo.capstone.cadence.notification.model;

/**
 * What a notification is about. Owned by the notification module; producers
 * publish domain events and never reference these.
 *
 * Wired: ATTENDANCE_OVERRIDDEN, COHORT_CHANGED, INVITE_ACCEPTED,
 * SUBMISSION_SENT_BACK, GRADES_RELEASED.
 * The catalogue later stages add is in the backend README ("Notifications").
 */
public enum NotificationType {
    ATTENDANCE_OVERRIDDEN,
    COHORT_CHANGED,
    INVITE_ACCEPTED,
    SUBMISSION_SENT_BACK,
    GRADES_RELEASED
}
