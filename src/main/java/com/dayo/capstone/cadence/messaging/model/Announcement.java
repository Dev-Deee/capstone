package com.dayo.capstone.cadence.messaging.model;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A one-way post from staff to one or more cohorts. Students actively enrolled
 * in any of the cohorts see it in their feed; nobody replies to it.
 */
@Entity
@Table(name = "announcement", indexes = {
        @Index(name = "idx_announcement_institution", columnList = "institution_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Announcement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    @ManyToMany
    @JoinTable(name = "announcement_cohort",
            joinColumns = @JoinColumn(name = "announcement_id"),
            inverseJoinColumns = @JoinColumn(name = "cohort_id"))
    private Set<Cohort> cohorts = new HashSet<>();
}
