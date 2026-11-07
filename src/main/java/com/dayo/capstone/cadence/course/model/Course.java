package com.dayo.capstone.cadence.course.model;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.course.enums.CourseStatus;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Course materials: sections of files and links, free to its audience. The
 * audience is either every student in the institution (allStudents) or the
 * students actively enrolled in the chosen cohorts. Paid courses come later.
 */
@Entity
@Table(name = "course")
@Getter
@Setter
@NoArgsConstructor
public class Course extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Required when a facilitator creates the course: what they teach.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "publish_at")
    private LocalDateTime publishAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "all_students", nullable = false, columnDefinition = "boolean default false")
    private boolean allStudents = false;

    @ManyToMany
    @JoinTable(name = "course_cohort",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "cohort_id"))
    private Set<Cohort> cohorts = new HashSet<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CourseSection> sections = new ArrayList<>();
}
