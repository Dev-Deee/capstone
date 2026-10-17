package com.dayo.capstone.cadence.assignment;

import com.dayo.capstone.cadence.assignment.enums.AssignmentStatus;
import com.dayo.capstone.cadence.assignment.model.Assignment;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.common.service.FileStorageService;
import com.dayo.capstone.cadence.config.AssignmentPublishJob;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Subject;
import com.dayo.capstone.cadence.support.IntegrationTestBase;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Facilitator attachments and "publish later". File storage is mocked, so
 * nothing is uploaded anywhere.
 */
class AssignmentAttachmentAndScheduleTest extends IntegrationTestBase {

    @MockitoBean FileStorageService fileStorage;
    @Autowired AssignmentPublishJob publishJob;
    @Autowired CohortEnrollmentRepository enrollmentRepository;

    Institution inst;
    Cohort cohort;
    Subject subject;
    User facilitator, student;
    Assignment draft;

    @BeforeEach
    void setUp() {
        inst = institution("Coursework College");
        cohort = cohort(inst);
        subject = subject(inst);
        facilitator = user("fac");
        member(facilitator, inst, Role.FACILITATOR);
        student = user("stu");
        member(student, inst, Role.STUDENT);
        CohortEnrollment e = new CohortEnrollment();
        e.setStudent(student);
        e.setCohort(cohort);
        e.setEnrolledAt(LocalDateTime.now().minusDays(1));
        enrollmentRepository.save(e);

        draft = new Assignment();
        draft.setCohort(cohort);
        draft.setSubject(subject);
        draft.setFacilitator(facilitator);
        draft.setTitle(unique("brief"));
        draft.setDeadline(LocalDateTime.now().plusDays(7));
        draft = assignmentRepository.save(draft);
    }

    private Resp upload(String filename, String token) {
        String boundary = "----cadence" + System.nanoTime();
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n"
                + "%PDF-1.4 test\r\n"
                + "--" + boundary + "--\r\n";
        try {
            HttpResponse<String> res = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/assignments/" + draft.getPublicId() + "/attachment"))
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

    private Resp schedule(String publishAt, String token) {
        Map<String, String> body = new HashMap<>();
        body.put("publishAt", publishAt);
        return call("PATCH", "/api/assignments/" + draft.getPublicId() + "/schedule", body, token);
    }

    @Test
    void facilitatorAttachesAndReplacesADocumentThatStudentsCanSee() {
        when(fileStorage.upload(any(), eq("brief.pdf"), eq("assignments"))).thenReturn("https://files.test/brief.pdf");
        when(fileStorage.upload(any(), eq("brief-v2.pdf"), eq("assignments"))).thenReturn("https://files.test/brief-v2.pdf");
        String token = login(facilitator);

        Resp first = upload("brief.pdf", token);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.text("attachmentUrl")).isEqualTo("https://files.test/brief.pdf");
        assertThat(first.text("attachmentName")).isEqualTo("brief.pdf");

        Resp second = upload("brief-v2.pdf", token);
        assertThat(second.text("attachmentName")).isEqualTo("brief-v2.pdf");
        verify(fileStorage).delete("https://files.test/brief.pdf");

        assertThat(get("/api/assignments/" + draft.getPublicId(), login(student)).text("attachmentUrl"))
                .isEqualTo("https://files.test/brief-v2.pdf");

        Resp removed = call("DELETE", "/api/assignments/" + draft.getPublicId() + "/attachment", null, token);
        assertThat(removed.status()).isEqualTo(200);
        assertThat(removed.text("attachmentUrl")).isNull();
    }

    @Test
    void anotherFacilitatorCannotAttachOrSchedule() {
        User other = user("otherfac");
        member(other, inst, Role.FACILITATOR);
        String token = login(other);
        assertThat(upload("x.pdf", token).status()).isEqualTo(403);
        assertThat(schedule(LocalDateTime.now().plusDays(1).withNano(0).toString(), token).status()).isEqualTo(403);
    }

    @Test
    void scheduleMustBeInTheFutureAndBeforeTheDeadline() {
        String token = login(facilitator);
        assertThat(schedule(LocalDateTime.now().minusHours(1).withNano(0).toString(), token).status()).isEqualTo(400);
        assertThat(schedule(LocalDateTime.now().plusDays(8).withNano(0).toString(), token).status()).isEqualTo(400);

        Resp ok = schedule(LocalDateTime.now().plusDays(1).withNano(0).toString(), token);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.text("publishAt")).isNotNull();
        assertThat(ok.text("status")).isEqualTo("DRAFT");

        assertThat(schedule(null, token).text("publishAt")).isNull();
    }

    @Test
    void theJobPublishesADueScheduledDraftLikePublishNow() {
        draft.setPublishAt(LocalDateTime.now().minusMinutes(1));
        assignmentRepository.save(draft);

        publishJob.publishDue();

        Assignment after = assignmentRepository.findById(draft.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AssignmentStatus.OPEN);
        assertThat(after.getPublishAt()).isNull();
        assertThat(submissionRepository.findAll().stream()
                .anyMatch(s -> s.getAssignment().getId().equals(draft.getId()) && s.getStudent().getId().equals(student.getId())))
                .isTrue();
    }
}
