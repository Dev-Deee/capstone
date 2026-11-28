package com.dayo.capstone.cadence.messaging.dto;

import java.util.List;

/**
 * Messages oldest → newest. {@code hasMore} means older messages exist before
 * the first item (ask again with {@code before} = its publicId).
 */
public record MessagePage(List<MessageResponse> items, boolean hasMore) {}
