package com.dayo.capstone.cadence.notification.model;

// IN_APP is what's delivered (and stored). EMAIL is sent alongside for a short
// whitelist of types (app.notifications.email-types). SMS is a stub for later.
public enum NotificationChannel {
    IN_APP, EMAIL, SMS
}
