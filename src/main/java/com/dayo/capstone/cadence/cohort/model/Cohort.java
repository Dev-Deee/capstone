package com.dayo.capstone.cadence.cohort.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.model.Institution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "cohort")
@Getter
@Setter
@NoArgsConstructor
public class Cohort extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean isActive = true;
}