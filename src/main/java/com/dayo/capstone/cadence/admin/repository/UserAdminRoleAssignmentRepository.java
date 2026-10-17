package com.dayo.capstone.cadence.admin.repository;

import com.dayo.capstone.cadence.admin.model.UserAdminRoleAssignment;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAdminRoleAssignmentRepository extends JpaRepository<UserAdminRoleAssignment, Long> {
    List<UserAdminRoleAssignment> findByUserAndIsActiveTrue(User user);
}