package com.dayo.capstone.cadence.course.dto;

import com.dayo.capstone.cadence.course.enums.CourseStatus;
import com.dayo.capstone.cadence.course.enums.MaterialKind;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record CourseResponse(
        UUID publicId,
        String title,
        String description,
        UUID subjectPublicId,
        String subjectName,
        boolean allStudents,
        List<CohortRef> cohorts,
        CourseStatus status,
        LocalDateTime publishAt,
        LocalDateTime publishedAt,
        String createdByName,
        LocalDateTime createdAt,
        boolean canEdit,
        int materialCount,
        List<Section> sections
) {
    public record CohortRef(UUID publicId, String name) {}

    @Builder
    public record Section(UUID publicId, String title, int position, List<Material> materials) {}

    @Builder
    public record Material(UUID publicId, String title, String note, MaterialKind kind, String url,
                           String fileName, String contentType, Long sizeBytes, int position) {}
}
