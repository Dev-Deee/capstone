package com.dayo.capstone.cadence.tenancy;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.SuperAdminSeeder;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SeederTest extends IntegrationTestBase {

    @Autowired SuperAdminSeeder seeder;

    @Test
    void seedsOwnerMembershipAndPlatformAdmin_andIsIdempotent() throws Exception {
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
        assertThat(admin.isPlatformAdmin()).isTrue();
        assertThat(membershipRepository.findByUserAndInstitution(admin, seeded).orElseThrow().getRoles())
                .containsExactly(Role.SUPER_ADMIN);
    }
}
