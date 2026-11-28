package com.dayo.capstone.cadence.messaging.service;

import com.dayo.capstone.cadence.admin.model.UserAdminRoleAssignment;
import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.CohortRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.common.event.AnnouncementPostedEvent;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.messaging.dto.AnnouncementPage;
import com.dayo.capstone.cadence.messaging.dto.AnnouncementRequest;
import com.dayo.capstone.cadence.messaging.dto.AnnouncementResponse;
import com.dayo.capstone.cadence.messaging.model.Announcement;
import com.dayo.capstone.cadence.messaging.repository.AnnouncementRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One-way cohort announcements. Who may post to a cohort:
 *   - a SUPER_ADMIN, any cohort in the institution;
 *   - an admin holding any active admin grant that covers the cohort
 *     (institution-wide, or scoped to that cohort);
 *   - a facilitator actively teaching the cohort (any subject).
 * Students actively enrolled in one of the cohorts read it in their feed and
 * get an in-app notification; nobody can reply.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final CohortRepository cohortRepository;
    private final CohortEnrollmentRepository enrollmentRepository;
    private final FacilitatorCohortRepository facilitatorCohortRepository;
    private final AdminPermissionService adminPermissionService;
    private final DomainEvents domainEvents;

    /** The active cohorts here this person may post to, by name. */
    @Transactional(readOnly = true)
    public List<AnnouncementResponse.CohortRef> audiences(User caller, Institution institution) {
        return postableCohorts(caller, institution).stream()
                .sorted(Comparator.comparing(Cohort::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(c -> new AnnouncementResponse.CohortRef(c.getPublicId(), c.getName()))
                .toList();
    }

    @Transactional
    public AnnouncementResponse post(AnnouncementRequest request, User caller, Institution institution) {
        Map<Long, Cohort> allowed = new LinkedHashMap<>();
        postableCohorts(caller, institution).forEach(c -> allowed.put(c.getId(), c));
        Set<Cohort> chosen = new LinkedHashSet<>();
        for (UUID id : request.getCohortPublicIds()) {
            Cohort cohort = cohortRepository.findByPublicId(id)
                    .filter(c -> c.getInstitution().getId().equals(institution.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Cohort not found: " + id));
            if (!allowed.containsKey(cohort.getId())) {
                throw new AccessDeniedException("You can't post announcements to " + cohort.getName());
            }
            chosen.add(cohort);
        }
        if (chosen.isEmpty()) throw new IllegalArgumentException("Choose at least one cohort");

        Announcement a = new Announcement();
        a.setInstitution(institution);
        a.setAuthor(caller);
        a.setTitle(request.getTitle().strip());
        a.setBody(request.getBody().strip());
        a.setCohorts(chosen);
        Announcement saved = announcementRepository.save(a);

        Set<Long> students = new LinkedHashSet<>();
        chosen.forEach(c -> enrollmentRepository.findByCohortAndIsActiveTrue(c)
                .forEach(e -> students.add(e.getStudent().getId())));
        if (!students.isEmpty()) {
            domainEvents.publish(new AnnouncementPostedEvent(institution.getId(), caller.getId(),
                    new ArrayList<>(students), saved.getTitle(), caller.getFullName(), saved.getPublicId()));
        }
        log.info("Announcement posted: publicId={}, cohorts={}, audience={}", saved.getPublicId(), chosen.size(), students.size());
        return toResponse(saved, caller);
    }

    /** A student's feed: announcements to cohorts they're actively enrolled in here. */
    @Transactional(readOnly = true)
    public AnnouncementPage feed(User student, Institution institution, int page, int size) {
        Page<Announcement> result = announcementRepository.findFeed(institution.getId(), student.getId(), pageable(page, size));
        return toPage(result, student);
    }

    /** Staff: what I've posted here; a super admin sees every announcement in the institution. */
    @Transactional(readOnly = true)
    public AnnouncementPage sent(User caller, Institution institution, int page, int size) {
        Page<Announcement> result = caller.hasRole(Role.SUPER_ADMIN)
                ? announcementRepository.findAllHere(institution.getId(), pageable(page, size))
                : announcementRepository.findByAuthor(institution.getId(), caller.getId(), pageable(page, size));
        return toPage(result, caller);
    }

    /** The author or a super admin can take an announcement down. */
    @Transactional
    public void delete(UUID publicId, User caller, Institution institution) {
        Announcement a = announcementRepository.findScoped(publicId, institution.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));
        if (!canDelete(a, caller)) throw new AccessDeniedException("You can't delete this announcement");
        announcementRepository.delete(a);
        log.info("Announcement deleted: publicId={}, byPublicId={}", publicId, caller.getPublicId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private List<Cohort> postableCohorts(User caller, Institution institution) {
        List<Cohort> here = cohortRepository.findByInstitution(institution).stream().filter(Cohort::isActive).toList();
        if (caller.hasRole(Role.SUPER_ADMIN)) return here;

        Set<Long> ids = new LinkedHashSet<>();
        if (caller.hasRole(Role.ADMIN)) {
            List<UserAdminRoleAssignment> grants = adminPermissionService.activeGrantsInCurrentInstitution(caller);
            if (grants.stream().anyMatch(g -> g.getCohort() == null)) return here;
            grants.forEach(g -> ids.add(g.getCohort().getId()));
        }
        if (caller.hasRole(Role.FACILITATOR)) {
            facilitatorCohortRepository.findByFacilitatorAndIsActiveTrue(caller).stream()
                    .map(FacilitatorCohort::getCohort)
                    .forEach(c -> ids.add(c.getId()));
        }
        return here.stream().filter(c -> ids.contains(c.getId())).toList();
    }

    private static boolean canDelete(Announcement a, User caller) {
        return a.getAuthor().getId().equals(caller.getId()) || caller.hasRole(Role.SUPER_ADMIN);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }

    private AnnouncementPage toPage(Page<Announcement> result, User viewer) {
        return new AnnouncementPage(result.getContent().stream().map(a -> toResponse(a, viewer)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }

    private static AnnouncementResponse toResponse(Announcement a, User viewer) {
        return new AnnouncementResponse(
                a.getPublicId(), a.getTitle(), a.getBody(),
                a.getAuthor().getFullName(), a.getAuthor().getPublicId(),
                a.getCohorts().stream()
                        .sorted(Comparator.comparing(Cohort::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                        .map(c -> new AnnouncementResponse.CohortRef(c.getPublicId(), c.getName())).toList(),
                a.getCreatedAt(), canDelete(a, viewer));
    }
}
