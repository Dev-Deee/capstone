package com.dayo.capstone.cadence.messaging.dto;

import java.util.List;

public record AnnouncementPage(List<AnnouncementResponse> items, int page, int size, long total, boolean hasMore) {}
