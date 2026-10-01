package com.dayo.capstone.cadence.admin.repository;

import com.dayo.capstone.cadence.admin.model.AdminRole;
import com.dayo.capstone.cadence.institution.model.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {
    Optional<AdminRole> findByPublicId(UUID publicId);
    List<AdminRole> findByInstitution(Institution institution);
}