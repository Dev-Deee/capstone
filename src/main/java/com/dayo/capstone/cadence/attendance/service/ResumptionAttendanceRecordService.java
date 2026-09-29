package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.MarkResumptionAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
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
public class ResumptionAttendanceRecordService {

    private final ResumptionAttendanceRecordRepository recordRepository;
    private final ResumptionSessionService resumptionSessionService;
    private final TotpService totpService;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;

    @Transactional
    public ResumptionAttendanceRecordResponse markAttendance(MarkResumptionAttendanceRequest request, User student) {
        // Resolve the student's active cohort — same gate as class attendance
        // (a valid code from another cohort must not let a student through).
        CohortEnrollment enrollment = cohortEnrollmentRepository
                .findByStudentAndIsActiveTrue(student)
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not enrolled in any active cohort"));

        Cohort cohort = enrollment.getCohort();

        // Find all currently-open resumption sessions for this cohort, then TOTP-validate.
        List<ResumptionSession> candidates = resumptionSessionService.findOpenResumptionSessionsByCohort(cohort);
        ResumptionSession session = candidates.stream()
                .filter(s -> totpService.isValid(s.getCodeSecret(), request.getResumptionCode(), TotpService.PERIOD_SECONDS))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Resumption code is invalid or has expired"));

        recordRepository.findByResumptionSessionAndStudent(session, student)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Resumption attendance already marked for today");
                });

        ResumptionAttendanceRecord record = new ResumptionAttendanceRecord();
        record.setResumptionSession(session);
        record.setStudent(student);
        record.setCheckedInAt(LocalDateTime.now());

        ResumptionAttendanceRecord saved = recordRepository.save(record);
        log.info("Resumption attendance marked: sessionPublicId={}, studentPublicId={}",
                session.getPublicId(), student.getPublicId());

        return toResponse(saved);
    }

    private ResumptionAttendanceRecordResponse toResponse(ResumptionAttendanceRecord record) {
        return ResumptionAttendanceRecordResponse.builder()
                .publicId(record.getPublicId())
                .resumptionSessionPublicId(record.getResumptionSession().getPublicId())
                .studentPublicId(record.getStudent().getPublicId())
                .checkedInAt(record.getCheckedInAt())
                .build();
    }
}
