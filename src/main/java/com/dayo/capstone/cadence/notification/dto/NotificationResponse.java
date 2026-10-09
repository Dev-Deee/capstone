package com.dayo.capstone.cadence.notification.dto;

import com.dayo.capstone.cadence.notification.model.Notification;
import com.dayo.capstone.cadence.notification.model.NotificationType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record NotificationResponse(
        UUID publicId,
        NotificationType type,
        String title,
        String body,
        String linkRoute,
        UUID linkPublicId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse of(Notification n) {
        return NotificationResponse.builder()
                .publicId(n.getPublicId()).type(n.getType()).title(n.getTitle()).body(n.getBody())
                .linkRoute(n.getLinkRoute()).linkPublicId(n.getLinkPublicId())
                .read(n.getReadAt() != null).createdAt(n.getCreatedAt())
                .build();
    }
}
