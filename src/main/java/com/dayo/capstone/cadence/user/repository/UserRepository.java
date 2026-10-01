package com.dayo.capstone.cadence.user.repository;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    List<User> findByInstitutionAndIsDeletedFalseOrderByCreatedAtDesc(Institution institution);
    // Checked on every authenticated request so deactivation takes effect immediately.
    boolean existsByPublicIdAndIsActiveTrueAndIsDeletedFalse(UUID publicId);
    Optional<User> findByInstitutionAndInstitutionStudentId(Institution institution, String institutionStudentId);
}