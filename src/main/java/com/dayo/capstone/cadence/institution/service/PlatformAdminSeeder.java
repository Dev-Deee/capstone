package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Seeds the Cadence platform admin from PLATFORM_ADMIN_EMAIL / PLATFORM_ADMIN_PASSWORD.
 * Skipped when the email is blank. Ensures the account exists and carries the
 * platform-admin flag; it belongs to no institution (it enters them from the
 * platform console). The password is only used when the account is created —
 * an existing account keeps its password, so changing it in the app sticks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.platform-admin.email:}")
    private String email;

    @Value("${app.seed.platform-admin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) return;
        String address = email.trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(address).orElseGet(() -> create(address));
        if (!user.isPlatformAdmin()) {
            user.setPlatformAdmin(true);
            userRepository.save(user);
            log.info("PlatformAdminSeeder: granted platform admin to {}", address);
        }
    }

    private User create(String address) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("PLATFORM_ADMIN_PASSWORD is required to create the platform admin " + address);
        }
        User user = new User();
        user.setFirstName("Platform");
        user.setLastName("Admin");
        user.setEmail(address);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setConsentGiven(true);
        user.setConsentTimestamp(LocalDateTime.now());
        user.setConsentVersion("v1");
        user.setAccountPending(false);
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        log.info("PlatformAdminSeeder: seeded platform admin {}", address);
        return saved;
    }
}
