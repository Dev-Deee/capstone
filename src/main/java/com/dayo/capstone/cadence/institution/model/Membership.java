package com.dayo.capstone.cadence.institution.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "membership", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "institution_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "membership_roles", joinColumns = @JoinColumn(name = "membership_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default true")
    private boolean isActive = true;

    public static Membership of(User user, Institution institution, Set<Role> roles) {
        Membership m = new Membership();
        m.setUser(user);
        m.setInstitution(institution);
        m.setRoles(new HashSet<>(roles));
        return m;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
