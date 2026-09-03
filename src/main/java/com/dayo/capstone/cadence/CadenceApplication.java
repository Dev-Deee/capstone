package com.dayo.capstone.cadence;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cadence — Academic Operations Platform, Semicolon Africa Capstone.
 *
 * Modular monolith (PRD Section 5): packages organized by feature/module
 * (institution, attendance, assignment, etc.), each with clean internal
 * boundaries — chosen so individual modules can be extracted into standalone
 * microservices later without a rewrite, once they mature and need
 * independent scaling.
 *
 * @EnableScheduling powers the platform's background jobs:
 *   - AssignmentDeadlineEnforcementJob (Section 7)
 *   - AttendancePatternDetectionJob (Section 6, 13.3)
 *   - GpsRetentionPurgeJob (Section 9)
 *   - LearnerInferenceJob (Section 13.3)
 *
 * @EnableAsync supports the event-driven notification backbone (Section 12):
 *   domain events fan out to NotificationListener, CohortEventListener,
 *   and AuditLogListener independently via Spring's ApplicationEventPublisher.
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })

@EnableScheduling
@EnableAsync
public class CadenceApplication {

    public static void main(String[] args) {
        // Loads .env into system properties for local development.
        // In Docker/CI, these are supplied as real environment variables instead.
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(CadenceApplication.class, args);
    }
}