package com.dayo.capstone.cadence.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PushSubscriptionResponse(UUID publicId, LocalDateTime createdAt) {}
