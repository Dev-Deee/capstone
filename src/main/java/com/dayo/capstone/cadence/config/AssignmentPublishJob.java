package com.dayo.capstone.cadence.config;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.assignment.repository.AssignmentRepository;
import com.dayo.capstone.cadence.assignment.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Publish later: every minute, scheduled drafts whose publish time has passed
 * go out exactly as if their facilitator had pressed Publish
 * (AssignmentService.publishNow).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentPublishJob {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentService assignmentService;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void publishDue() {
        List<Assignment> due = assignmentRepository
                .findByStatusAndPublishAtLessThanEqual(AssignmentStatus.DRAFT, LocalDateTime.now());
        for (Assignment assignment : due) {
            assignmentService.publishNow(assignment);
        }
        if (!due.isEmpty()) log.info("AssignmentPublishJob: published {} scheduled assignment(s)", due.size());
    }
}
