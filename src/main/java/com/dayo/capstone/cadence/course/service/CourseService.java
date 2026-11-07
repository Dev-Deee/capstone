package com.dayo.capstone.cadence.course.service;

import com.dayo.capstone.cadence.admin.service.AdminPermissionService;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.cohort.service.CohortService;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.common.event.CoursePublishedEvent;
import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.common.service.FileStorageService;
import com.dayo.capstone.cadence.course.dto.CourseRequest;
import com.dayo.capstone.cadence.course.dto.CourseResponse;
import com.dayo.capstone.cadence.course.dto.LinkMaterialRequest;
import com.dayo.capstone.cadence.course.dto.MaterialUpdateRequest;
import com.dayo.capstone.cadence.course.dto.MoveRequest;
import com.dayo.capstone.cadence.course.enums.CourseStatus;
import com.dayo.capstone.cadence.course.enums.MaterialKind;
import com.dayo.capstone.cadence.course.model.Course;
import com.dayo.capstone.cadence.course.model.CourseMaterial;
import com.dayo.capstone.cadence.course.model.CourseSection;
import com.dayo.capstone.cadence.course.repository.CourseMaterialRepository;
import com.dayo.capstone.cadence.course.repository.CourseRepository;
import com.dayo.capstone.cadence.course.repository.CourseSectionRepository;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.institution.repository.MembershipRepository;
import com.dayo.capstone.cadence.institution.service.SubjectService;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Course materials. Who may create and edit a course:
 *   - a SUPER_ADMIN, any course in the institution;
 *   - an admin with MANAGE_COURSES, for cohorts their grant covers (a course
 *     for every student needs an institution-wide grant);
 *   - a facilitator, for a subject they teach in each chosen cohort, and only
 *     their own courses afterwards.
 * Students see published courses whose audience includes them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    public static final long MAX_FILE_BYTES = 100L * 1024 * 1024; // Cloudinary's free-plan cap per file

    private final CourseRepository courseRepository;
    private final CourseSectionRepository sectionRepository;
    private final CourseMaterialRepository materialRepository;
    private final CohortService cohortService;
    private final SubjectService subjectService;
    private final FacilitatorCohortRepository facilitatorCohortRepository;
    private final CohortEnrollmentRepository enrollmentRepository;
    private final MembershipRepository membershipRepository;
    private final AdminPermissionService adminPermissionService;
    private final FileStorageService fileStorageService;
    private final DomainEvents domainEvents;

    // ── Reading ────────────────────────────────────────────────────────────

    // Courses this person can edit (drafts included).
    @Transactional(readOnly = true)
    public List<CourseResponse> listManageable(User caller, Institution institution) {
        return courseRepository.findByInstitutionOrderByCreatedAtDesc(institution).stream()
                .filter(c -> canEdit(c, caller))
                .map(c -> toResponse(c, true))
                .toList();
    }

    // Published courses a student can see.
    @Transactional(readOnly = true)
    public List<CourseResponse> listForStudent(User student, Institution institution) {
        return courseRepository.findByInstitutionAndStatusOrderByPublishedAtDesc(institution, CourseStatus.PUBLISHED).stream()
                .filter(c -> isInAudience(c, student))
                .map(c -> toResponse(c, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse get(UUID publicId, User caller, Institution institution) {
        Course course = findScoped(publicId, institution);
        boolean editable = canEdit(course, caller);
        if (!editable && !(course.getStatus() == CourseStatus.PUBLISHED && isInAudience(course, caller))) {
            throw new ResourceNotFoundException("Course not found: " + publicId);
        }
        return toResponse(course, editable);
    }

    // ── Courses ────────────────────────────────────────────────────────────

    @Transactional
    public CourseResponse create(CourseRequest request, User caller, Institution institution) {
        Course course = new Course();
        course.setInstitution(institution);
        course.setCreatedBy(caller);
        applyDetails(course, request, caller, institution);
        Course saved = courseRepository.save(course);
        log.info("Course created: publicId={}, byPublicId={}", saved.getPublicId(), caller.getPublicId());
        return toResponse(saved, true);
    }

    @Transactional
    public CourseResponse update(UUID publicId, CourseRequest request, User caller, Institution institution) {
        Course course = editable(publicId, caller, institution);
        applyDetails(course, request, caller, institution);
        return toResponse(courseRepository.save(course), true);
    }

    @Transactional
    public void delete(UUID publicId, User caller, Institution institution) {
        Course course = editable(publicId, caller, institution);
        List<String> files = course.getSections().stream()
                .flatMap(s -> s.getMaterials().stream())
                .filter(m -> m.getKind() == MaterialKind.FILE)
                .map(CourseMaterial::getUrl)
                .toList();
        courseRepository.delete(course);
        files.forEach(this::deleteFileQuietly);
        log.info("Course deleted: publicId={}, byPublicId={}", publicId, caller.getPublicId());
    }

    @Transactional
    public CourseResponse publish(UUID publicId, User caller, Institution institution) {
        Course course = editable(publicId, caller, institution);
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            throw new IllegalArgumentException("This course is already published");
        }
        return toResponse(publishNow(course, caller.getId()), true);
    }

    // Back to a draft: students stop seeing it; nothing is deleted.
    @Transactional
    public CourseResponse unpublish(UUID publicId, User caller, Institution institution) {
        Course course = editable(publicId, caller, institution);
        course.setStatus(CourseStatus.DRAFT);
        course.setPublishAt(null);
        return toResponse(courseRepository.save(course), true);
    }

    // Publish later, or with null clear the schedule.
    @Transactional
    public CourseResponse schedule(UUID publicId, LocalDateTime publishAt, User caller, Institution institution) {
        Course course = editable(publicId, caller, institution);
        if (course.getStatus() != CourseStatus.DRAFT) {
            throw new IllegalArgumentException("Only a draft can be scheduled");
        }
        if (publishAt != null && !publishAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Pick a publish time in the future");
        }
        course.setPublishAt(publishAt);
        return toResponse(courseRepository.save(course), true);
    }

    // Shared by Publish and CoursePublishJob: the audience is told once, on publishing.
    @Transactional
    public Course publishNow(Course course, Long actorId) {
        course.setStatus(CourseStatus.PUBLISHED);
        course.setPublishAt(null);
        course.setPublishedAt(LocalDateTime.now());
        Course saved = courseRepository.save(course);
        List<Long> audience = audienceStudentIds(saved);
        if (!audience.isEmpty()) {
            domainEvents.publish(new CoursePublishedEvent(saved.getInstitution().getId(), actorId,
                    audience, saved.getTitle(), saved.getPublicId()));
        }
        log.info("Course published: publicId={}, audience={}", saved.getPublicId(), audience.size());
        return saved;
    }

    // ── Sections ───────────────────────────────────────────────────────────

    @Transactional
    public CourseResponse addSection(UUID coursePublicId, String title, User caller, Institution institution) {
        Course course = editable(coursePublicId, caller, institution);
        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setTitle(title.trim());
        section.setPosition(course.getSections().size());
        course.getSections().add(section);
        sectionRepository.save(section); // persisted now, so it has its public id
        return toResponse(course, true);
    }

    @Transactional
    public CourseResponse renameSection(UUID sectionPublicId, String title, User caller, Institution institution) {
        CourseSection section = sectionEditable(sectionPublicId, caller, institution);
        section.setTitle(title.trim());
        sectionRepository.save(section);
        return toResponse(section.getCourse(), true);
    }

    @Transactional
    public CourseResponse deleteSection(UUID sectionPublicId, User caller, Institution institution) {
        CourseSection section = sectionEditable(sectionPublicId, caller, institution);
        Course course = section.getCourse();
        List<String> files = section.getMaterials().stream()
                .filter(m -> m.getKind() == MaterialKind.FILE).map(CourseMaterial::getUrl).toList();
        course.getSections().remove(section);
        renumber(course.getSections());
        Course saved = courseRepository.save(course);
        files.forEach(this::deleteFileQuietly);
        return toResponse(saved, true);
    }

    @Transactional
    public CourseResponse moveSection(UUID sectionPublicId, MoveRequest.Direction direction, User caller, Institution institution) {
        CourseSection section = sectionEditable(sectionPublicId, caller, institution);
        Course course = section.getCourse();
        move(course.getSections(), section, direction);
        renumber(course.getSections());
        return toResponse(courseRepository.save(course), true);
    }

    // ── Materials ──────────────────────────────────────────────────────────

    @Transactional
    public CourseResponse addFile(UUID sectionPublicId, String title, String note, byte[] bytes, String fileName,
                                  String contentType, User caller, Institution institution) {
        CourseSection section = sectionEditable(sectionPublicId, caller, institution);
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Choose a file to upload");
        if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("Files can be at most 100 MB. Use a Google Drive link for bigger ones.");
        String url = fileStorageService.upload(bytes, fileName, "courses");
        CourseMaterial material = newMaterial(section, blankTo(title, fileName), note, MaterialKind.FILE, url);
        material.setFileName(fileName);
        material.setContentType(contentType);
        material.setSizeBytes((long) bytes.length);
        section.getMaterials().add(material);
        materialRepository.save(material);
        return toResponse(section.getCourse(), true);
    }

    @Transactional
    public CourseResponse addLink(UUID sectionPublicId, LinkMaterialRequest request, User caller, Institution institution) {
        CourseSection section = sectionEditable(sectionPublicId, caller, institution);
        CourseMaterial material = newMaterial(section, request.getTitle(), request.getNote(), MaterialKind.LINK, request.getUrl().trim());
        section.getMaterials().add(material);
        materialRepository.save(material);
        return toResponse(section.getCourse(), true);
    }

    @Transactional
    public CourseResponse updateMaterial(UUID materialPublicId, MaterialUpdateRequest request, User caller, Institution institution) {
        CourseMaterial material = materialEditable(materialPublicId, caller, institution);
        material.setTitle(request.getTitle().trim());
        material.setNote(blankTo(request.getNote(), null));
        materialRepository.save(material);
        return toResponse(material.getSection().getCourse(), true);
    }

    @Transactional
    public CourseResponse deleteMaterial(UUID materialPublicId, User caller, Institution institution) {
        CourseMaterial material = materialEditable(materialPublicId, caller, institution);
        CourseSection section = material.getSection();
        section.getMaterials().remove(material);
        renumber(section.getMaterials());
        sectionRepository.save(section);
        if (material.getKind() == MaterialKind.FILE) deleteFileQuietly(material.getUrl());
        return toResponse(section.getCourse(), true);
    }

    @Transactional
    public CourseResponse moveMaterial(UUID materialPublicId, MoveRequest.Direction direction, User caller, Institution institution) {
        CourseMaterial material = materialEditable(materialPublicId, caller, institution);
        CourseSection section = material.getSection();
        move(section.getMaterials(), material, direction);
        renumber(section.getMaterials());
        sectionRepository.save(section);
        return toResponse(section.getCourse(), true);
    }

    // ── Rules ──────────────────────────────────────────────────────────────

    private void applyDetails(Course course, CourseRequest request, User caller, Institution institution) {
        Set<Cohort> cohorts = new LinkedHashSet<>();
        if (!request.isAllStudents()) {
            if (request.getCohortPublicIds() == null || request.getCohortPublicIds().isEmpty()) {
                throw new IllegalArgumentException("Choose at least one cohort, or every student");
            }
            for (String id : request.getCohortPublicIds()) {
                cohorts.add(cohortService.findEntityByPublicIdScopedToInstitution(UUID.fromString(id), institution));
            }
        }
        Subject subject = request.getSubjectPublicId() == null || request.getSubjectPublicId().isBlank() ? null
                : subjectService.findEntityByPublicIdScopedToInstitution(UUID.fromString(request.getSubjectPublicId()), institution);
        requireMayTarget(caller, request.isAllStudents(), cohorts, subject);
        course.setTitle(request.getTitle().trim());
        course.setDescription(blankTo(request.getDescription(), null));
        course.setSubject(subject);
        course.setAllStudents(request.isAllStudents());
        course.setCohorts(new HashSet<>(cohorts));
    }

    // May this person give a course this audience?
    private void requireMayTarget(User caller, boolean allStudents, Set<Cohort> cohorts, Subject subject) {
        if (caller.hasRole(Role.SUPER_ADMIN) || adminCovers(caller, allStudents, cohorts)) return;
        if (caller.hasRole(Role.FACILITATOR)) {
            if (allStudents) throw new AccessDeniedException("Facilitators can share courses with the cohorts they teach, not every student");
            if (subject == null) throw new IllegalArgumentException("Choose the subject this course is for");
            boolean teachesAll = cohorts.stream().allMatch(c -> facilitatorCohortRepository
                    .findByFacilitatorAndCohortAndSubject(caller, c, subject).filter(FacilitatorCohort::isActive).isPresent());
            if (teachesAll) return;
            throw new AccessDeniedException("You can only share a course with cohorts where you teach " + subject.getName());
        }
        throw new AccessDeniedException("Missing required permission: " + Permission.MANAGE_COURSES);
    }

    private boolean adminCovers(User caller, boolean allStudents, Set<Cohort> cohorts) {
        if (allStudents) {
            return adminPermissionService.activeGrantsInCurrentInstitution(caller).stream()
                    .anyMatch(a -> a.getCohort() == null && a.getAdminRole().getPermissions().contains(Permission.MANAGE_COURSES));
        }
        return !cohorts.isEmpty() && cohorts.stream()
                .allMatch(c -> adminPermissionService.hasPermissionForCohort(caller, Permission.MANAGE_COURSES, c));
    }

    boolean canEdit(Course course, User caller) {
        if (caller.hasRole(Role.SUPER_ADMIN)) return true;
        if (course.getCreatedBy().getId().equals(caller.getId())) return true;
        return adminCovers(caller, course.isAllStudents(), course.getCohorts());
    }

    private boolean isInAudience(Course course, User student) {
        if (!student.hasRole(Role.STUDENT)) return false;
        if (course.isAllStudents()) return true;
        return course.getCohorts().stream().anyMatch(c -> enrollmentRepository.findByStudentAndCohort(student, c)
                .filter(CohortEnrollment::isActive).isPresent());
    }

    private List<Long> audienceStudentIds(Course course) {
        if (course.isAllStudents()) {
            return membershipRepository.findActiveUserIdsWithRole(course.getInstitution().getId(), Role.STUDENT);
        }
        Set<Long> ids = new LinkedHashSet<>();
        course.getCohorts().forEach(c -> enrollmentRepository.findByCohortAndIsActiveTrue(c)
                .forEach(e -> ids.add(e.getStudent().getId())));
        return new ArrayList<>(ids);
    }

    // ── Lookups ────────────────────────────────────────────────────────────

    private Course findScoped(UUID publicId, Institution institution) {
        Course course = courseRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + publicId));
        if (!course.getInstitution().getId().equals(institution.getId())) {
            throw new ResourceNotFoundException("Course not found: " + publicId);
        }
        return course;
    }

    private Course editable(UUID publicId, User caller, Institution institution) {
        Course course = findScoped(publicId, institution);
        if (!canEdit(course, caller)) {
            // Someone who can see it gets a clear refusal; anyone else, "not found".
            if (course.getStatus() == CourseStatus.PUBLISHED && isInAudience(course, caller)) {
                throw new AccessDeniedException("You can't edit this course");
            }
            throw new ResourceNotFoundException("Course not found: " + publicId);
        }
        return course;
    }

    private CourseSection sectionEditable(UUID sectionPublicId, User caller, Institution institution) {
        CourseSection section = sectionRepository.findByPublicId(sectionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found: " + sectionPublicId));
        editable(section.getCourse().getPublicId(), caller, institution);
        return section;
    }

    private CourseMaterial materialEditable(UUID materialPublicId, User caller, Institution institution) {
        CourseMaterial material = materialRepository.findByPublicId(materialPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + materialPublicId));
        editable(material.getSection().getCourse().getPublicId(), caller, institution);
        return material;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static CourseMaterial newMaterial(CourseSection section, String title, String note, MaterialKind kind, String url) {
        CourseMaterial m = new CourseMaterial();
        m.setSection(section);
        m.setTitle(title.trim());
        m.setNote(blankTo(note, null));
        m.setKind(kind);
        m.setUrl(url);
        m.setPosition(section.getMaterials().size());
        return m;
    }

    private static <T> void move(List<T> list, T item, MoveRequest.Direction direction) {
        int i = list.indexOf(item);
        int j = direction == MoveRequest.Direction.UP ? i - 1 : i + 1;
        if (i < 0 || j < 0 || j >= list.size()) return;
        list.set(i, list.get(j));
        list.set(j, item);
    }

    private static void renumber(List<?> items) {
        for (int i = 0; i < items.size(); i++) {
            Object o = items.get(i);
            if (o instanceof CourseSection s) s.setPosition(i);
            if (o instanceof CourseMaterial m) m.setPosition(i);
        }
    }

    private static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private void deleteFileQuietly(String url) {
        try {
            fileStorageService.delete(url);
        } catch (Exception e) {
            log.warn("Couldn't delete a course file from storage: url={}", url, e);
        }
    }

    private CourseResponse toResponse(Course c, boolean canEdit) {
        List<CourseResponse.Section> sections = c.getSections().stream()
                .sorted(Comparator.comparingInt(CourseSection::getPosition))
                .map(s -> CourseResponse.Section.builder()
                        .publicId(s.getPublicId()).title(s.getTitle()).position(s.getPosition())
                        .materials(s.getMaterials().stream()
                                .sorted(Comparator.comparingInt(CourseMaterial::getPosition))
                                .map(m -> CourseResponse.Material.builder()
                                        .publicId(m.getPublicId()).title(m.getTitle()).note(m.getNote())
                                        .kind(m.getKind()).url(m.getUrl()).fileName(m.getFileName())
                                        .contentType(m.getContentType()).sizeBytes(m.getSizeBytes())
                                        .position(m.getPosition()).build())
                                .toList())
                        .build())
                .toList();
        return CourseResponse.builder()
                .publicId(c.getPublicId())
                .title(c.getTitle())
                .description(c.getDescription())
                .subjectPublicId(c.getSubject() != null ? c.getSubject().getPublicId() : null)
                .subjectName(c.getSubject() != null ? c.getSubject().getName() : null)
                .allStudents(c.isAllStudents())
                .cohorts(c.getCohorts().stream()
                        .sorted(Comparator.comparing(Cohort::getName))
                        .map(co -> new CourseResponse.CohortRef(co.getPublicId(), co.getName())).toList())
                .status(c.getStatus())
                .publishAt(c.getPublishAt())
                .publishedAt(c.getPublishedAt())
                .createdByName(c.getCreatedBy().getFullName())
                .createdAt(c.getCreatedAt())
                .canEdit(canEdit)
                .materialCount(sections.stream().mapToInt(s -> s.materials().size()).sum())
                .sections(sections)
                .build();
    }
}
