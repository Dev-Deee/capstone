package com.dayo.capstone.cadence.assignment.repository;

import com.dayo.capstone.cadence.assignment.model.Submission;
import com.dayo.capstone.cadence.assignment.model.SubmissionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionAttemptRepository extends JpaRepository<SubmissionAttempt, Long> {
    List<SubmissionAttempt> findBySubmissionOrderByAttemptNumberAsc(Submission submission);
    int countBySubmission(Submission submission);
}
