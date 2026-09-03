package com.dayo.capstone.cadence.admin.repository;

import com.dayo.capstone.cadence.admin.model.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {
    Optional<AdminRole> findByPublicId(UUID publicId);
}