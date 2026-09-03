package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.MarkAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.AttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.enums.AttendanceStatus;
import com.dayo.capstone.cadence.attendance.enums.VerificationMethod;
import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.attendance.repository.AttendanceRecordRepository;
import com.dayo.capstone.cadence.attendance.strategy.SessionCodeStrategy;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecordService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SessionService sessionService;
    private final SessionCodeStrategy sessionCodeStrategy;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;

    @Transactional
    public AttendanceRecordResponse markAttendance(MarkAttendanceRequest request, User student) {
        Session session = sessionService.findEntityBySessionCode(request.getSessionCode());

        // The student must actually be enrolled in this session's cohort —
        // a valid code alone isn't enough (Section 18.2's late-joiner
        // logic depends on enrolment being the real gate, not just the code).
        cohortEnrollmentRepository.findByStudentAndCohort(student, session.getCohort())
                .filter(e -> e.isActive())
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not enrolled in this session's cohort"));

        attendanceRecordRepository.findBySessionAndStudent(session, student)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Attendance already marked for this session");
                });

        if (!sessionCodeStrategy.verify(session, request.getSessionCode())) {
            throw new IllegalArgumentException("Session code is invalid or has expired");
        }

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