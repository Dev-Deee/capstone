package com.dayo.capstone.cadence.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the conversation list. {@code canSend} is false when the pairing no longer qualifies. */
public record ConversationResponse(
        UUID publicId,
        PersonRef other,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        boolean lastMessageFromMe,
        long unreadCount,
        boolean canSend
) {}
