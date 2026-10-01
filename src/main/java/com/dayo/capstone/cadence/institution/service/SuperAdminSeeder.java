package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.repository.InstitutionRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.super-admin.email}")
    private String superAdminEmail;

    @Value("${app.seed.super-admin.password}")
    private String superAdminPassword;

    @Value("${app.seed.institution.name:Semicolon Africa}")
    private String institutionName;

    @Override
    public void run(ApplicationArguments args) {
        Institution institution = institutionRepository.findByName(institutionName)
                .orElseGet(this::seedInstitution);

        boolean superAdminExists = userRepository.findByEmail(superAdminEmail).isPresent();

        if (superAdminExists) {
            log.info("SuperAdminSeeder: Super Admin already exists, skipping");
            return;
        }

        User superAdmin = new User();
        superAdmin.setInstitution(institution);
        superAdmin.setFirstName("Super");
        superAdmin.setLastName("Admin");
        superAdmin.setEmail(superAdminEmail);
        superAdmin.setPhoneNumber("0000000000");
        superAdmin.setPasswordHash(passwordEncoder.encode(superAdminPassword));
        superAdmin.setRoles(Set.of(Role.SUPER_ADMIN));
        superAdmin.setConsentGiven(true);
        superAdmin.setConsentTimestamp(LocalDateTime.now());
        superAdmin.setConsentVersion("v1");
        superAdmin.setAccountPending(false);
        superAdmin.setEmailVerified(true);

        userRepository.save(superAdmin);
        log.info("SuperAdminSeeder: seeded Super Admin with email={}", superAdminEmail);
    }

    private Institution seedInstitution() {
        Institution institution = new Institution();
        institution.setName(institutionName);
        institution.setAddress("Lagos, Nigeria");
        Institution saved = institutionRepository.save(institution);
        log.info("SuperAdminSeeder: seeded Institution '{}'", institutionName);
        return saved;
    }
}