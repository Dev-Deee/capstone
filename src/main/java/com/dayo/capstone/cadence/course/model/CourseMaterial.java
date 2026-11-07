package com.dayo.capstone.cadence.course.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.course.enums.MaterialKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "course_material")
@Getter
@Setter
@NoArgsConstructor
public class CourseMaterial extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private CourseSection section;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialKind kind;

    // FILE: stored file; LINK: the address given.
    @Column(nullable = false, length = 2000)
    private String url;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false)
    private int position;
}
