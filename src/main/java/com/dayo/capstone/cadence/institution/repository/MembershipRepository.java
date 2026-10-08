package com.dayo.capstone.cadence.institution.repository;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByUserAndInstitution(User user, Institution institution);
    boolean existsByUserAndInstitution(User user, Institution institution);
    List<Membership> findByUserOrderByCreatedAtAsc(User user);
    @Query("SELECT m FROM Membership m JOIN FETCH m.institution WHERE m.user.publicId = :userPublicId AND m.institution.publicId = :institutionPublicId")
    Optional<Membership> findByUserPublicIdAndInstitutionPublicId(
            @Param("userPublicId") UUID userPublicId,
            @Param("institutionPublicId") UUID institutionPublicId);
    @Query("SELECT m FROM Membership m WHERE m.institution = :institution AND m.user.isDeleted = false ORDER BY m.createdAt DESC")
    List<Membership> findMembers(@Param("institution") Institution institution);
    @Query("SELECT COUNT(m) FROM Membership m JOIN m.roles r WHERE m.institution = :institution AND r = :role AND m.isActive = true AND m.user.isDeleted = false")
    long countActiveWithRole(@Param("institution") Institution institution, @Param("role") Role role);
    @Query("SELECT m.user.id FROM Membership m WHERE m.institution.id = :institutionId AND m.user.id IN :userIds AND m.isActive = true AND m.user.isDeleted = false")
    List<Long> findReachableUserIds(@Param("institutionId") Long institutionId, @Param("userIds") java.util.Collection<Long> userIds);
    @Query("SELECT r FROM Membership m JOIN m.roles r WHERE m.user.id = :userId AND m.institution.id = :institutionId")
    List<Role> findReachableRoles(@Param("userId") Long userId, @Param("institutionId") Long institutionId);
}
