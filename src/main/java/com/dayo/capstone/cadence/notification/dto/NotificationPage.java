package com.dayo.capstone.cadence.notification.dto;

import java.util.List;

public record NotificationPage(List<NotificationResponse> items, int page, int size, long total, boolean hasMore) {}
