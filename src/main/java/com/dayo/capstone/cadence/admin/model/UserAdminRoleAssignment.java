package com.dayo.capstone.cadence.admin.model;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_admin_role_assignment")
@Getter
@Setter
@NoArgsConstructor
public class UserAdminRoleAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id", nullable = false)
    private AdminRole adminRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id") // nullable = institution-wide
    private Cohort cohort;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    private boolean isActive = true;
}