package com.dayo.capstone.cadence.assignment.repository;

import com.dayo.capstone.cadence.assignment.enums.SubmissionStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByPublicId(UUID publicId);
    Optional<Submission> findByAssignmentAndStudent(Assignment assignment, User student);
    List<Submission> findByStudent(User student);
    List<Submission> findByAssignment(Assignment assignment);
    List<Submission> findByStatus(SubmissionStatus status);
}
