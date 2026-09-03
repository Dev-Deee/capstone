package com.dayo.capstone.cadence.institution.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.enums.GuardianLinkageMode;
import com.dayo.capstone.cadence.institution.enums.GuardianNotificationDefault;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * PRD Sections 4, 5 — root of the schema. Every institution-scoped entity
 * (Cohort, Hall, User, etc.) references this via institutionId, enforced
 * at the service layer per Section 18.1's multi-tenant isolation rule.
 */
@Entity
@Table(name = "institution")
@Getter
@Setter
@NoArgsConstructor
public class Institution extends BaseEntity {

    private String name;
    private String address;
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    private GuardianLinkageMode guardianLinkageMode = GuardianLinkageMode.OPTIONAL_STUDENT_CONSENT;

    @Enumerated(EnumType.STRING)
    private GuardianNotificationDefault guardianNotificationDefault = GuardianNotificationDefault.DAILY_DIGEST;

    // Add to Institution.java
    @Column(name = "resumption_opens_at")
    private LocalTime resumptionOpensAt = LocalTime.of(7, 0);

    @Column(name = "resumption_window_minutes")
    private Integer resumptionWindowMinutes = 90;
}