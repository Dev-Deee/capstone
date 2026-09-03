package com.dayo.capstone.cadence.config;

import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.repository.ResumptionSessionRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.repository.CohortRepository;
import com.dayo.capstone.cadence.institution.model.Institution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumptionSessionGenerationJob {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CohortRepository cohortRepository;
    private final ResumptionSessionRepository resumptionSessionRepository;

    @Scheduled(cron = "0 0 0 * * *") // midnight
    @Transactional
    public void generateDailyResumptionSessions() {
        LocalDate today = LocalDate.now();
        List<Cohort> activeCohorts = cohortRepository.findByIsActiveTrue();

        int created = 0;
        for (Cohort cohort : activeCohorts) {
            boolean exists = resumptionSessionRepository
                    .findByCohortAndResumptionDate(cohort, today).isPresent();
            if (!exists) {
                createSession(cohort, today);
                created++;
            }
        }
        log.info("ResumptionSessionGenerationJob: {} cohorts processed, {} new sessions created",
                activeCohorts.size(), created);
    }

    private void createSession(Cohort cohort, LocalDate today) {
        Institution institution = cohort.getInstitution();
        LocalDateTime opensAt = LocalDateTime.of(today, institution.getResumptionOpensAt());

        ResumptionSession session = new ResumptionSession();
        session.setCohort(cohort);
        session.setResumptionDate(today);
        session.setResumptionCode(generateCode());
        session.setQrToken(UUID.randomUUID().toString());
        session.setOpensAt(opensAt);
        session.setExpiresAt(opensAt.plusMinutes(institution.getResumptionWindowMinutes()));

        resumptionSessionRepository.save(session);
        log.info("Resumption session generated: cohortPublicId={}, date={}", cohort.getPublicId(), today);
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}