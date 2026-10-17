package com.dayo.capstone.cadence.institution.service;

import com.dayo.capstone.cadence.audit.enums.AuditAction;
import com.dayo.capstone.cadence.audit.service.AuditLogService;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.dto.request.ChangeInstitutionStatusRequest;
import com.dayo.capstone.cadence.institution.dto.response.PlatformInstitutionRow;
import com.dayo.capstone.cadence.institution.dto.response.PlatformOverview;
import com.dayo.capstone.cadence.institution.enums.InstitutionStatus;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.institution.repository.InstitutionRepository;
import com.dayo.capstone.cadence.institution.repository.MembershipRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The platform console (platform admins only — enforced by the controller):
 * every institution at a glance, and the one place an institution's status
 * is changed. Status is what decides whether members can use an institution
 * (Institution.isUsable), so each change carries a reason and is audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformService {

    private final InstitutionRepository institutionRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PlatformOverview overview() {
        List<Institution> all = institutionRepository.findAll();
        Map<InstitutionStatus, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(Institution::getStatus, Collectors.counting()));
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        long memberships = membershipRepository.countActiveMembersByInstitution().stream()
                .mapToLong(r -> (Long) r[1]).sum();
        return PlatformOverview.builder()
                .institutions(all.size())
                .active(byStatus.getOrDefault(InstitutionStatus.ACTIVE, 0L))
                .pending(byStatus.getOrDefault(InstitutionStatus.PENDING, 0L))
                .suspended(byStatus.getOrDefault(InstitutionStatus.SUSPENDED, 0L))
                .newLast30Days(all.stream().filter(i -> i.getCreatedAt().isAfter(monthAgo)).count())
                .users(userRepository.countByIsDeletedFalse())
                .memberships(memberships)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PlatformInstitutionRow> institutions() {
        Map<Long, Long> members = membershipRepository.countActiveMembersByInstitution().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, User> owners = new HashMap<>();
        for (Membership m : membershipRepository.findActiveWithRole(Role.SUPER_ADMIN)) {
            owners.putIfAbsent(m.getInstitution().getId(), m.getUser()); // oldest first
        }
        return institutionRepository.findAll().stream()
                .sorted(Comparator.comparing(Institution::getCreatedAt).reversed())
                .map(i -> {
                    User owner = owners.get(i.getId());
                    return PlatformInstitutionRow.builder()
                            .publicId(i.getPublicId()).name(i.getName()).slug(i.getSlug())
                            .status(i.getStatus()).createdAt(i.getCreatedAt())
                            .members(members.getOrDefault(i.getId(), 0L))
                            .ownerName(owner == null ? null : owner.getFullName())
                            .ownerEmail(owner == null ? null : owner.getEmail())
                            .build();
                })
                .toList();
    }

    /** Suspend, reactivate or mark pending. Takes effect on members' next request. */
    @Transactional
    public PlatformInstitutionRow changeStatus(UUID publicId, ChangeInstitutionStatusRequest request, User caller) {
        Institution institution = institutionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found: " + publicId));
        InstitutionStatus before = institution.getStatus();
        if (before == request.getStatus()) {
            throw new IllegalArgumentException(institution.getName() + " is already " + before.name().toLowerCase());
        }
        institution.setStatus(request.getStatus());
        institutionRepository.save(institution);

        String reason = request.getReason().trim();
        auditLogService.log(caller.getPublicId(), "[PLATFORM_ADMIN]", AuditAction.UPDATE, "Institution", publicId,
                "{\"status\":\"" + before + "\"}",
                "{\"status\":\"" + request.getStatus() + "\",\"reason\":" + json(reason) + "}");
        log.info("Institution status changed: institutionPublicId={}, {} -> {}, by={}",
                publicId, before, request.getStatus(), caller.getPublicId());
        return institutions().stream().filter(r -> r.publicId().equals(publicId)).findFirst().orElseThrow();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    // A JSON string literal for the audit entry (quotes, backslashes, new lines escaped).
    private static String json(String s) {
        try {
            return JSON.writeValueAsString(s);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
