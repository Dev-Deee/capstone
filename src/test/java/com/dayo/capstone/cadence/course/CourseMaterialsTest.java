package com.dayo.capstone.cadence.course;

import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.model.FacilitatorCohort;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.cohort.repository.FacilitatorCohortRepository;
import com.dayo.capstone.cadence.common.enums.Permission;
import com.dayo.capstone.cadence.common.service.FileStorageService;
import com.dayo.capstone.cadence.config.CoursePublishJob;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.notification.model.Notification;
import com.dayo.capstone.cadence.notification.model.NotificationType;
import com.dayo.capstone.cadence.notification.repository.NotificationRepository;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Course materials: who may create courses for whom, sections and materials,
 * and that students see only published courses meant for them. File storage
 * is mocked, so nothing is uploaded anywhere.
 */
class CourseMaterialsTest extends IntegrationTestBase {

    @MockitoBean FileStorageService fileStorage;
    @Autowired CohortEnrollmentRepository enrollmentRepository;
    @Autowired FacilitatorCohortRepository facilitatorCohortRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TransactionTemplate tx;
    @Autowired CoursePublishJob publishJob;

    Institution inst;
    Cohort cohortA, cohortB;
    Subject subject;
    User facilitator, otherFacilitator, studentA, studentB, owner;

    @BeforeEach
    void setUp() {
        inst = institution("Course College");
        cohortA = cohort(inst);
        cohortB = cohort(inst);
        subject = subject(inst);
        owner = user("owner");
        member(owner, inst, Role.SUPER_ADMIN);
        facilitator = user("fac");
        member(facilitator, inst, Role.FACILITATOR);
        teach(facilitator, cohortA);
        otherFacilitator = user("fac2");
        member(otherFacilitator, inst, Role.FACILITATOR);
        teach(otherFacilitator, cohortA);
        studentA = student("stuA", cohortA);
        studentB = student("stuB", cohortB);
        when(fileStorage.upload(any(), any(), eq("courses"))).thenReturn("https://files.test/courses/notes.pdf");
    }

    private void teach(User f, Cohort c) {
        FacilitatorCohort fc = new FacilitatorCohort();
        fc.setFacilitator(f);
        fc.setCohort(c);
        fc.setSubject(subject);
        fc.setAssignedAt(LocalDateTime.now());
        facilitatorCohortRepository.save(fc);
    }

    private User student(String label, Cohort c) {
        User s = user(label);
        member(s, inst, Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(s);
        e.setCohort(c);
        e.setEnrolledAt(LocalDateTime.now().minusDays(1));
        enrollmentRepository.save(e);
        return s;
    }

    private Map<String, Object> course(String title, boolean allStudents, Cohort... cohorts) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("subjectPublicId", subject.getPublicId().toString());
        m.put("allStudents", allStudents);
        m.put("cohortPublicIds", java.util.Arrays.stream(cohorts).map(c -> c.getPublicId().toString()).toList());
        return m;
    }

    private Resp create(Map<String, Object> body, String token) {
        return call("POST", "/api/courses", body, token);
    }

    private String sectionOf(Resp course, int i) {
        return course.body().get("sections").get(i).get("publicId").asText();
    }

    private Resp uploadFile(String sectionId, String token) {
        String boundary = "----cadence" + System.nanoTime();
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nWeek 1 notes\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"notes.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n%PDF-1.4 test\r\n"
                + "--" + boundary + "--\r\n";
        try {
            HttpResponse<String> res = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/course-sections/" + sectionId + "/files"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Resp(res.statusCode(), res.body().isBlank() ? null : new ObjectMapper().readTree(res.body()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("Timed out waiting for the notification");
    }

    private List<NotificationType> typesOf(User u) {
        return tx.execute(s -> notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(u.getId()))
                .map(Notification::getType).toList());
    }

    @Test
    void aFacilitatorSharesOnlyWithCohortsWhereTheyTeachTheSubject() {
        String token = login(facilitator);
        assertThat(create(course("Intro to HTTP", false, cohortA), token).status()).isEqualTo(201);
        assertThat(create(course("Not mine", false, cohortB), token).status()).isEqualTo(403);
        assertThat(create(course("Everyone", true), token).status()).isEqualTo(403);
        Map<String, Object> noSubject = course("No subject", false, cohortA);
        noSubject.remove("subjectPublicId");
        assertThat(create(noSubject, token).message()).contains("subject");
    }

    @Test
    void anAdminNeedsManageCoursesAndOnlyForTheCohortsTheirGrantCovers() {
        User admin = user("admin");
        member(admin, inst, Role.ADMIN);
        assertThat(create(course("Nope", false, cohortA), login(admin)).status()).isEqualTo(403);

        grant(admin, inst, cohortA, Permission.MANAGE_COURSES);
        String token = login(admin);
        assertThat(create(course("Cohort A pack", false, cohortA), token).status()).isEqualTo(201);
        assertThat(create(course("Cohort B pack", false, cohortB), token).status()).isEqualTo(403);
        assertThat(create(course("Everyone", true), token).status()).isEqualTo(403);

        User wide = user("admin2");
        member(wide, inst, Role.ADMIN);
        grant(wide, inst, Permission.MANAGE_COURSES);
        assertThat(create(course("Handbook", true), login(wide)).status()).isEqualTo(201);
    }

    @Test
    void sectionsHoldUploadedFilesAndDriveLinksInOrder() {
        String token = login(facilitator);
        Resp c = create(course("Week by week", false, cohortA), token);
        String id = c.text("publicId");
        Resp withSections = call("POST", "/api/courses/" + id + "/sections", Map.of("title", "Week 1"), token);
        withSections = call("POST", "/api/courses/" + id + "/sections", Map.of("title", "Week 2"), token);
        assertThat(withSections.body().get("sections").size()).isEqualTo(2);
        String week1 = sectionOf(withSections, 0);
        assertThat(week1).isNotEqualTo("null");

        Resp uploaded = uploadFile(week1, token);
        assertThat(uploaded.status()).as(uploaded.toString()).isEqualTo(200);
        verify(fileStorage).upload(any(), eq("notes.pdf"), eq("courses"));

        Resp linked = call("POST", "/api/course-sections/" + week1 + "/links",
                Map.of("title", "Slides", "url", "https://drive.google.com/file/d/abc/view"), token);
        assertThat(linked.status()).as(linked.toString()).isEqualTo(200);
        JsonNode materials = linked.body().get("sections").get(0).get("materials");
        assertThat(materials.size()).isEqualTo(2);
        assertThat(materials.get(0).get("kind").asText()).isEqualTo("FILE");
        assertThat(materials.get(0).get("title").asText()).isEqualTo("Week 1 notes");
        assertThat(materials.get(1).get("kind").asText()).isEqualTo("LINK");
        assertThat(materials.get(1).get("publicId").asText()).isNotEqualTo("null");

        assertThat(call("POST", "/api/course-sections/" + week1 + "/links",
                Map.of("title", "Bad", "url", "ftp://nope"), token).status()).isEqualTo(400);

        // Move the link above the file, then the second week above the first.
        Resp moved = call("POST", "/api/course-materials/" + materials.get(1).get("publicId").asText() + "/move",
                Map.of("direction", "UP"), token);
        assertThat(moved.body().get("sections").get(0).get("materials").get(0).get("kind").asText()).isEqualTo("LINK");
        Resp reordered = call("POST", "/api/course-sections/" + sectionOf(moved, 1) + "/move", Map.of("direction", "UP"), token);
        assertThat(reordered.body().get("sections").get(0).get("title").asText()).isEqualTo("Week 2");

        // Deleting a file material removes the stored file too.
        String fileId = reordered.body().get("sections").get(1).get("materials").get(1).get("publicId").asText();
        assertThat(call("DELETE", "/api/course-materials/" + fileId, null, token).status()).isEqualTo(200);
        verify(fileStorage).delete("https://files.test/courses/notes.pdf");
    }

    @Test
    void studentsSeeOnlyPublishedCoursesMeantForThemAndAreNotified() {
        String token = login(facilitator);
        String id = create(course("Cohort A only", false, cohortA), token).text("publicId");
        call("POST", "/api/courses/" + id + "/sections", Map.of("title", "Start here"), token);

        String a = login(studentA), b = login(studentB);
        assertThat(get("/api/courses/mine", a).body().size()).isZero();          // still a draft
        assertThat(get("/api/courses/" + id, a).status()).isEqualTo(404);

        assertThat(call("POST", "/api/courses/" + id + "/publish", null, token).status()).isEqualTo(200);
        assertThat(get("/api/courses/mine", a).body().size()).isEqualTo(1);
        assertThat(get("/api/courses/" + id, a).body().get("canEdit").asBoolean()).isFalse();
        assertThat(get("/api/courses/mine", b).body().size()).isZero();          // other cohort
        assertThat(get("/api/courses/" + id, b).status()).isEqualTo(404);
        await(() -> typesOf(studentA).contains(NotificationType.COURSE_PUBLISHED));
        assertThat(typesOf(studentB)).doesNotContain(NotificationType.COURSE_PUBLISHED);

        // A student can't edit it.
        assertThat(call("POST", "/api/courses/" + id + "/sections", Map.of("title", "Mine"), a).status()).isEqualTo(403);

        // A course for every student reaches the other cohort too.
        String all = create(course("Student handbook", true), login(owner)).text("publicId");
        call("POST", "/api/courses/" + all + "/publish", null, login(owner));
        assertThat(get("/api/courses/mine", b).body().size()).isEqualTo(1);
    }

    @Test
    void onlyTheCreatorAmongFacilitatorsCanEditTheirCourse() {
        String id = create(course("Mine", false, cohortA), login(facilitator)).text("publicId");
        String other = login(otherFacilitator);
        assertThat(call("POST", "/api/courses/" + id + "/sections", Map.of("title", "Hijack"), other).status()).isEqualTo(404);
        assertThat(get("/api/courses/manage", other).body().size()).isZero();
        assertThat(get("/api/courses/manage", login(owner)).body().size()).isEqualTo(1);
    }

    @Test
    void aScheduledCoursePublishesOnItsOwn() {
        String token = login(facilitator);
        String id = create(course("Later", false, cohortA), token).text("publicId");
        String when = LocalDateTime.now().plusMinutes(5).withNano(0).toString();
        assertThat(call("PATCH", "/api/courses/" + id + "/schedule", Map.of("publishAt", when), token).status()).isEqualTo(200);

        var course = tx.execute(s -> {
            var c = courseRepository.findByPublicId(java.util.UUID.fromString(id)).orElseThrow();
            c.setPublishAt(LocalDateTime.now().minusMinutes(1));
            return courseRepository.save(c);
        });
        publishJob.publishDue();
        assertThat(get("/api/courses/" + id, token).text("status")).isEqualTo("PUBLISHED");
    }

    @Autowired com.dayo.capstone.cadence.course.repository.CourseRepository courseRepository;
}
