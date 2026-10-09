package com.dayo.capstone.cadence.institution.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.institution.enums.GuardianLinkageMode;
import com.dayo.capstone.cadence.institution.enums.GuardianNotificationDefault;
import com.dayo.capstone.cadence.institution.enums.InstitutionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "institution")
@Getter
@Setter
@NoArgsConstructor
public class Institution extends BaseEntity {

    private String name;

    @Column(unique = true, nullable = false, updatable = false, length = 50)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstitutionStatus status = InstitutionStatus.ACTIVE;
    private String address;

    @Enumerated(EnumType.STRING)
    private GuardianLinkageMode guardianLinkageMode = GuardianLinkageMode.OPTIONAL_STUDENT_CONSENT;

    @Enumerated(EnumType.STRING)
    private GuardianNotificationDefault guardianNotificationDefault = GuardianNotificationDefault.DAILY_DIGEST;

    @Column(name = "resumption_opens_at")
    private LocalTime resumptionOpensAt = LocalTime.of(7, 0);

    @Column(name = "resumption_window_minutes")
    private Integer resumptionWindowMinutes = 90;

    /**
     * The one rule for whether an institution can be used: its status is
     * ACTIVE. PENDING and SUSPENDED institutions can't be signed into, switched
     * into or joined, and open sessions lose their scope there — except for a
     * platform admin, who keeps full reach (see SessionTokenService.rolesIn
     * and JwtAuthFilter).
     */
    public boolean isUsable() {
        return status == InstitutionStatus.ACTIVE;
    }
}