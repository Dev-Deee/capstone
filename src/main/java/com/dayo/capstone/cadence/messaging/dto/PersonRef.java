package com.dayo.capstone.cadence.messaging.dto;

import java.util.List;
import java.util.UUID;

/** Someone on the other end of a conversation, or someone you may message. */
public record PersonRef(UUID publicId, String name, List<String> roles, String profileImageUrl) {}
