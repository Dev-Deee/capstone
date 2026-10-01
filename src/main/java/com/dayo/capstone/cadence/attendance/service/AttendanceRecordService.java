package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.MarkAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import com.dayo.capstone.cadence.attendance.enums.VerificationMethod;
import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.AttendanceRecordRepository;
import com.dayo.capstone.cadence.cohort.model.Cohort;
import com.dayo.capstone.cadence.cohort.model.CohortEnrollment;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecordService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SessionService sessionService;
    private final TotpService totpService;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;

    @Transactional
    public AttendanceRecordResponse markAttendance(MarkAttendanceRequest request, User student) {
        // Resolve the student's active cohort — enrollment is the real gate
        // (Section 18.2). A valid code from a different cohort's session must
        // not let a student mark attendance against a session they have no
        // business attending.
        CohortEnrollment enrollment = cohortEnrollmentRepository
                .findByStudentAndIsActiveTrue(student)
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not enrolled in any active cohort"));

        Cohort cohort = enrollment.getCohort();

        // Find all currently-open sessions for this cohort, then TOTP-validate.
        // There will normally be at most one open session per cohort at a time,
        // but we check all to handle any overlap edge case — first match wins.
        List<Session> candidates = sessionService.findOpenSessionsByCohort(cohort);
        Session session = candidates.stream()
                .filter(s -> totpService.isValid(s.getCodeSecret(), request.getSessionCode(), TotpService.PERIOD_SECONDS))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Session code is invalid or has expired"));

        attendanceRecordRepository.findBySessionAndStudent(session, student)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Attendance already marked for this session");
                });

        AttendanceRecord record = new AttendanceRecord();
        record.setSession(session);
        record.setStudent(student);
        record.setStatus(AttendanceStatus.PRESENT);
        record.setVerificationMethod(VerificationMethod.CODE);
        record.setSubmittedAt(LocalDateTime.now());

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        log.info("Attendance marked: sessionPublicId={}, studentPublicId={}",
                session.getPublicId(), student.getPublicId());

        return toResponse(saved);
    }

    // PRD Section 6.6 — facilitator manual override, distinct code path
    // from normal verification, mandatory reason recorded.
    @Transactional
    public AttendanceRecordResponse manualOverride(
            java.util.UUID sessionPublicId, java.util.UUID studentPublicId,
            String reason, User overridingFacilitator
    ) {
        Session session = sessionService.findEntityByPublicId(sessionPublicId);

        if (!session.getFacilitator().getId().equals(overridingFacilitator.getId())) {
            throw new IllegalArgumentException("Only the facilitator who opened this session may override");
        }

        // Note: student lookup by publicId needs UserService injected here
        // once this method is wired into a controller — omitted from this
        // constructor for brevity, add UserService as a dependency when
        // building the override endpoint.

        throw new UnsupportedOperationException(
                "Wire UserService lookup + AuditLog entry here before exposing via controller " +
                        "(Section 6.6's 15% alert threshold also needs a running count check at this point)");
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord record) {
        return AttendanceRecordResponse.builder()
                .publicId(record.getPublicId())
                .sessionPublicId(record.getSession().getPublicId())
                .studentPublicId(record.getStudent().getPublicId())
                .status(record.getStatus())
                .verificationMethod(record.getVerificationMethod())
                .submittedAt(record.getSubmittedAt())
                .build();
    }
}
