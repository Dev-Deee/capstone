package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.PlatformAdminSeeder;
import com.dayo.capstone.cadence.institution.service.SuperAdminSeeder;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeederTest extends IntegrationTestBase {

    @Autowired SuperAdminSeeder seeder;
    @Autowired PlatformAdminSeeder platformSeeder;

    @Test
    void seedsInstitutionOwner_andIsIdempotent() throws Exception {
        // It already ran at startup; run it twice more.
        long institutions = institutionRepository.count();
        long users = userRepository.count();
        long memberships = membershipRepository.count();
        seeder.run(null);
        seeder.run(null);
        assertThat(institutionRepository.count()).isEqualTo(institutions);
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(membershipRepository.count()).isEqualTo(memberships);

        Institution seeded = institutionRepository.findByName("Seed Institution").orElseThrow();
        assertThat(seeded.getSlug()).isEqualTo("seed-institution");
        User admin = userRepository.findByEmail("seed-admin@test.cadence").orElseThrow();
        assertThat(admin.isPlatformAdmin()).isFalse(); // platform admin is seeded separately
        assertThat(membershipRepository.findByUserAndInstitution(admin, seeded).orElseThrow().getRoles())
                .containsExactly(Role.SUPER_ADMIN);
    }

    @Test
    void seedsAPlatformAdminWithNoInstitutionWhoCanSignInAndUseTheConsole() throws Exception {
        long users = userRepository.count();
        platformSeeder.run(null);
        assertThat(userRepository.count()).isEqualTo(users);

        User platform = userRepository.findByEmail("platform-seed@test.cadence").orElseThrow();
        assertThat(platform.isPlatformAdmin()).isTrue();
        assertThat(membershipRepository.findAll()).noneMatch(m -> m.getUser().getId().equals(platform.getId()));

        Resp in = call("POST", "/api/auth/login",
                Map.of("identifier", "platform-seed@test.cadence", "password", "Platform-Seed-1!"), null);
        assertThat(in.status()).as(in.toString()).isEqualTo(200);
        assertThat(get("/api/platform/overview", in.text("accessToken")).status()).isEqualTo(200);
    }
}
