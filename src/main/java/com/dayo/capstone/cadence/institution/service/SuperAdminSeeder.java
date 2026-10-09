package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.institution.repository.InstitutionRepository;
import com.dayo.capstone.cadence.institution.repository.MembershipRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;

/**
 * Local-development seed, from .env. Ensures — each step independently, so
 * re-running is always a no-op once everything exists:
 *   1. the seed institution (with a slug),
 *   2. the seed user,
 *   3. their SUPER_ADMIN membership in that institution (institution owner).
 * Platform oversight is a different power, seeded separately by
 * {@link PlatformAdminSeeder}; app.seed.super-admin.platform-admin=true still
 * grants it to this user too, for setups without a separate platform admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.super-admin.email}")
    private String superAdminEmail;

    @Value("${app.seed.super-admin.password}")
    private String superAdminPassword;

    @Value("${app.seed.institution.name:Semicolon Africa}")
    private String institutionName;

    @Value("${app.seed.super-admin.platform-admin:false}")
    private boolean grantPlatformAdmin;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Institution institution = institutionRepository.findByName(institutionName)
                .orElseGet(this::seedInstitution);

        User user = userRepository.findByEmail(superAdminEmail).orElseGet(this::seedUser);

        if (!membershipRepository.existsByUserAndInstitution(user, institution)) {
            membershipRepository.save(Membership.of(user, institution, EnumSet.of(Role.SUPER_ADMIN)));
            log.info("SuperAdminSeeder: granted SUPER_ADMIN of '{}' to {}", institutionName, superAdminEmail);
        }

        if (grantPlatformAdmin && !user.isPlatformAdmin()) {
            user.setPlatformAdmin(true);
            userRepository.save(user);
            log.info("SuperAdminSeeder: granted platform admin to {}", superAdminEmail);
        }
    }

    private Institution seedInstitution() {
        Institution institution = new Institution();
        institution.setName(institutionName);
        institution.setSlug(InstitutionOnboardingService.slugify(institutionName));
        institution.setAddress("Lagos, Nigeria");
        Institution saved = institutionRepository.save(institution);
        log.info("SuperAdminSeeder: seeded Institution '{}' ({})", institutionName, saved.getSlug());
        return saved;
    }

    private User seedUser() {
        User user = new User();
        user.setFirstName("Super");
        user.setLastName("Admin");
        user.setEmail(superAdminEmail);
        user.setPhoneNumber("0000000000");
        user.setPasswordHash(passwordEncoder.encode(superAdminPassword));
        user.setConsentGiven(true);
        user.setConsentTimestamp(LocalDateTime.now());
        user.setConsentVersion("v1");
        user.setAccountPending(false);
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        log.info("SuperAdminSeeder: seeded user {}", superAdminEmail);
        return saved;
    }
}
