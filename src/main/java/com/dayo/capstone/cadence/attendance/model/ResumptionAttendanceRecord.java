package com.dayo.capstone.cadence.attendance.model;

import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import com.dayo.capstone.cadence.attendance.enums.VerificationMethod;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumption_attendance_record", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"resumption_session_id", "student_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ResumptionAttendanceRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resumption_session_id", nullable = false)
    private ResumptionSession resumptionSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "checked_in_at", nullable = false)
    private LocalDateTime checkedInAt;

    private Double latitude;
    private Double longitude;

    @Column(name = "matched_institution_network")
    private Boolean matchedInstitutionNetwork;

    @Column(name = "web_authn_verified")
    private Boolean webAuthnVerified;

    private Integer confidenceScore;

    // Nullable for rows created before manual overrides existed — read
    // through getEffectiveStatus(), where null means a normal code check-in.
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method")
    private VerificationMethod verificationMethod;

    @Column(name = "override_reason")
    private String overrideReason;

    public AttendanceStatus getEffectiveStatus() {
        return status != null ? status : AttendanceStatus.PRESENT;
    }
}