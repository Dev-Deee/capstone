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

/**
 * Membership is the multi-tenant trust boundary. Every lookup here is keyed by
 * an institution the caller already resolved from TenantContext — there is
 * deliberately no "find membership by publicId" without an institution.
 */
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByUserAndInstitution(User user, Institution institution);

    boolean existsByUserAndInstitution(User user, Institution institution);

    List<Membership> findByUserOrderByCreatedAtAsc(User user);

    // Used by JwtAuthFilter on every request: the caller's roles in the
    // institution named by their token, straight from the database, so a
    // demotion or suspension takes effect immediately.
    @Query("SELECT m FROM Membership m WHERE m.user.publicId = :userPublicId AND m.institution.publicId = :institutionPublicId")
    Optional<Membership> findByUserPublicIdAndInstitutionPublicId(
            @Param("userPublicId") UUID userPublicId,
            @Param("institutionPublicId") UUID institutionPublicId);

    @Query("SELECT m FROM Membership m WHERE m.institution = :institution AND m.user.isDeleted = false ORDER BY m.createdAt DESC")
    List<Membership> findMembers(@Param("institution") Institution institution);

    // Last-SUPER_ADMIN guard: how many active owners an institution has.
    @Query("SELECT COUNT(m) FROM Membership m JOIN m.roles r WHERE m.institution = :institution AND r = :role AND m.isActive = true AND m.user.isDeleted = false")
    long countActiveWithRole(@Param("institution") Institution institution, @Param("role") Role role);
}
