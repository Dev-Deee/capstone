package com.dayo.capstone.cadence.notification.model;

/**
 * What a notification is about. Owned by the notification module; producers
 * publish domain events and never reference these.
 *
 * Wired today (Stage 4): ATTENDANCE_OVERRIDDEN, COHORT_CHANGED, INVITE_ACCEPTED.
 * The catalogue later stages add is in the backend README ("Notifications").
 */
public enum NotificationType {
    ATTENDANCE_OVERRIDDEN,
    COHORT_CHANGED,
    INVITE_ACCEPTED
}
