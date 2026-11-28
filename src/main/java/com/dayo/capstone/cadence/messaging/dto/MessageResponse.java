package com.dayo.capstone.cadence.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageResponse(UUID publicId, UUID senderPublicId, String senderName, boolean mine,
                              String body, LocalDateTime createdAt) {}
