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
 * @EnableScheduling powers the background jobs that exist today:
 *   - AssignmentDeadlineEnforcementJob (Section 7) — marks overdue work missing
 *   - AssignmentPublishJob — publishes scheduled ("publish later") assignments
 *   - TokenCleanupJob — purges expired refresh and blacklisted tokens
 *   - NotificationCleanupJob (Section 18.9) — purges read notifications after 180 days
 *
 * @EnableAsync runs the event-driven notification backbone (Section 12):
 *   producers publish domain events (common.event) through DomainEvents;
 *   NotificationListener turns them into in-app notifications after commit,
 *   on the eventExecutor. Outgoing email runs on the mailExecutor (AsyncConfig).
 *   Audit logging is still written directly by services, not via events.
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