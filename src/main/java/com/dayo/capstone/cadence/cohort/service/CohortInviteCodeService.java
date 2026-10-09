package com.dayo.capstone.cadence.cohort.service;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.cohort.dto.request.CreateInviteCodeRequest;
import com.dayo.capstone.cadence.cohort.dto.request.SendInviteCodeRequest;
import com.dayo.capstone.cadence.cohort.dto.response.CohortResponse;
import com.dayo.capstone.cadence.cohort.dto.response.InviteCodeResponse;
import com.dayo.capstone.cadence.cohort.dto.response.InviteCodeSendResult;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.common.validation.ValidationPatterns;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.model.CohortInviteCode;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.CohortInviteCodeRepository;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.service.MembershipService;
import com.dayo.capstone.cadence.security.service.EmailService;
import com.dayo.capstone.cadence.security.service.SmsService;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CohortInviteCodeService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0/I/1 — avoids ambiguity when read aloud or handwritten
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final CohortInviteCodeRepository inviteCodeRepository;
    private final CohortService cohortService;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;
    private final CohortEnrollmentService cohortEnrollmentService;
    private final AdminPermissionService adminPermissionService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final MembershipService membershipService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public InviteCodeResponse create(CreateInviteCodeRequest request, Institution callerInstitution) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(
                UUID.fromString(request.getCohortPublicId()), callerInstitution);

        CohortInviteCode invite = new CohortInviteCode();
        invite.setCohort(cohort);
        invite.setCode(generateCode());
        invite.setMaxUses(request.getMaxUses() != null ? request.getMaxUses() : 0);
        invite.setExpiresAt(request.getExpiresInDays() != null
                ? LocalDateTime.now().plusDays(request.getExpiresInDays())
                : null);

        CohortInviteCode saved = inviteCodeRepository.save(invite);
        log.info("Invite code generated: cohortPublicId={}, code={}", cohort.getPublicId(), saved.getCode());

        return toResponse(saved);
    }

    // Codes for one cohort, newest first — lets an admin find and re-share an
    // existing code instead of minting a new one every time.
    @Transactional(readOnly = true)
    public List<InviteCodeResponse> listForCohort(UUID cohortPublicId, Institution callerInstitution, User caller) {
        Cohort cohort = cohortService.findEntityByPublicIdScopedToInstitution(cohortPublicId, callerInstitution);
        requireCanManage(caller, cohort);
        return inviteCodeRepository.findByCohortOrderByCreatedAtDesc(cohort).stream()
                .map(this::toResponse)
                .toList();
    }

    // Emails and/or texts the code. Each message carries both paths: a signup
    // link with the code prefilled (new students) and the bare code for
    // existing students to enter under "Join a cohort" on their dashboard.
    @Transactional(readOnly = true)
    public InviteCodeSendResult send(UUID inviteCodePublicId, SendInviteCodeRequest request,
                                     Institution callerInstitution, User caller) {
        CohortInviteCode invite = inviteCodeRepository.findByPublicId(inviteCodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite code not found: " + inviteCodePublicId));
        Cohort cohort = invite.getCohort();
        if (!cohort.getInstitution().getId().equals(callerInstitution.getId())) {
            throw new ResourceNotFoundException("Invite code not found: " + inviteCodePublicId);
        }
        requireCanManage(caller, cohort);
        if (!invite.isValid()) {
            throw new IllegalArgumentException("This invite code has expired, been revoked or used up — generate a new one");
        }

        List<String> emails = clean(request.getEmails());
        // Phones arrive as typed: spaces, dashes, brackets and dots are dropped first.
        List<String> phones = clean(request.getPhoneNumbers()).stream()
                .map(p -> p.replaceAll("[\\s().-]", "")).toList();
        emails.stream().filter(e -> !ValidationPatterns.isEmail(e)).findFirst().ifPresent(bad -> {
            throw new IllegalArgumentException("Invalid email address: " + bad + ". " + ValidationPatterns.EMAIL_MESSAGE);
        });
        phones.stream().filter(p -> !ValidationPatterns.isPhone(p)).findFirst().ifPresent(bad -> {
            throw new IllegalArgumentException("Invalid phone number: " + bad + ". " + ValidationPatterns.PHONE_MESSAGE);
        });
        if (emails.isEmpty() && phones.isEmpty()) {
            throw new IllegalArgumentException("Add at least one email address or phone number");
        }

        String code = invite.getCode();
        String signupLink = frontendUrl + "/register?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
        String institutionName = cohort.getInstitution().getName();
        String expiryLine = invite.getExpiresAt() != null
                ? "This code expires on " + invite.getExpiresAt().format(EXPIRY_FORMAT) + ".\n"
                : "";

        String subject = "Your invite to join " + cohort.getName() + " on Cadence";
        String emailBody = "Hello,\n\n"
                + "You've been invited to join " + cohort.getName() + " at " + institutionName + " on Cadence.\n\n"
                + "Your invite code: " + code + "\n\n"
                + "New to Cadence? Create your account here — the code is filled in for you:\n"
                + signupLink + "\n\n"
                + "Already have a Cadence student account? Log in and enter the code under "
                + "\"Join a cohort\" on your dashboard.\n\n"
                + expiryLine;
        String smsBody = "Cadence: you're invited to join " + cohort.getName() + ". Invite code: " + code
                + ". Sign up: " + signupLink + " (existing students: enter the code on your dashboard).";

        emails.forEach(to -> emailService.send(to, subject, emailBody));
        phones.forEach(to -> smsService.send(to, smsBody));

        log.info("Invite code shared: code={}, cohortPublicId={}, emails={}, sms={}, by={}",
                code, cohort.getPublicId(), emails.size(), phones.size(), caller.getPublicId());
        return new InviteCodeSendResult(emails.size(), phones.size());
    }

    // An existing, logged-in student redeeming a code. The app assumes a single
    // active enrollment per student, so moving cohorts stays an admin action.
    @Transactional
    public CohortResponse join(String rawCode, User student) {
        CohortInviteCode invite = resolveRedeemable(rawCode);
        Cohort cohort = invite.getCohort();


        cohortEnrollmentRepository.findByStudentAndIsActiveTrue(student).ifPresent(existing -> {
            if (existing.getCohort().getId().equals(cohort.getId())) {
                throw new IllegalArgumentException("You are already enrolled in " + cohort.getName());
            }
            throw new IllegalArgumentException("You are already enrolled in " + existing.getCohort().getName()
                    + ". Ask an admin if you need to move to a different cohort.");
        });
        cohortEnrollmentRepository.findByStudentAndCohort(student, cohort)
                .filter(e -> !e.isActive())
                .ifPresent(e -> {
                    throw new IllegalArgumentException("Your earlier enrollment in " + cohort.getName()
                            + " was ended. Ask an admin to re-enroll you.");
                });

        // The code proves the right to join; a student from another institution
        // becomes a STUDENT member of this one (multi-tenant).
        if (!student.isMemberOf(cohort.getInstitution().getId())) {
            membershipService.add(student, cohort.getInstitution(), EnumSet.of(Role.STUDENT));
        }
        CohortEnrollment enrollment = cohortEnrollmentService.enroll(student, cohort);
        markUsed(invite);

        log.info("Student joined cohort via invite code: userPublicId={}, cohortPublicId={}",
                student.getPublicId(), cohort.getPublicId());
        return cohortService.getByPublicId(enrollment.getCohort().getPublicId());
    }

    // Shared by self-registration and join — one place decides whether a code is usable.
    public CohortInviteCode resolveRedeemable(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        CohortInviteCode invite = inviteCodeRepository.findByCode(code)
                .filter(CohortInviteCode::isValid)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invite code"));
        if (!invite.getCohort().isActive()) {
            throw new IllegalArgumentException("This cohort is no longer active");
        }
        if (!invite.getCohort().getInstitution().isActive()) {
            throw new IllegalArgumentException("This institution is no longer active");
        }
        return invite;
    }

    public void markUsed(CohortInviteCode invite) {
        invite.setUseCount(invite.getUseCount() + 1);
        inviteCodeRepository.save(invite);
    }

    // Same rule as code creation in CohortController.
    private void requireCanManage(User caller, Cohort cohort) {
        if (!caller.hasRole(Role.SUPER_ADMIN)) {
            adminPermissionService.requirePermissionForCohort(caller, Permission.MANAGE_INVITE_CODES, cohort);
        }
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(v -> v.toLowerCase(Locale.ROOT).contains("@") ? v.toLowerCase(Locale.ROOT) : v)
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
    }

    private InviteCodeResponse toResponse(CohortInviteCode invite) {
        return InviteCodeResponse.builder()
                .publicId(invite.getPublicId())
                .code(invite.getCode())
                .cohortPublicId(invite.getCohort().getPublicId())
                .expiresAt(invite.getExpiresAt())
                .maxUses(invite.getMaxUses())
                .useCount(invite.getUseCount())
                .valid(invite.isValid())
                .createdAt(invite.getCreatedAt())
                .build();
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
