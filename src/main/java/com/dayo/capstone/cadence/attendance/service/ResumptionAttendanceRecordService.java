package com.dayo.capstone.cadence.attendance.service;

import com.dayo.capstone.cadence.attendance.dto.request.MarkResumptionAttendanceRequest;
import com.dayo.capstone.cadence.attendance.dto.response.ResumptionAttendanceRecordResponse;
import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.attendance.repository.ResumptionAttendanceRecordRepository;
import com.dayo.capstone.cadence.cohort.repository.CohortEnrollmentRepository;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumptionAttendanceRecordService {

    private final ResumptionAttendanceRecordRepository recordRepository;
    private final ResumptionSessionService resumptionSessionService;
    private final CohortEnrollmentRepository cohortEnrollmentRepository;

    @Transactional
    public ResumptionAttendanceRecordResponse markAttendance(MarkResumptionAttendanceRequest request, User student) {
        ResumptionSession session = resumptionSessionService.findEntityByCode(request.getResumptionCode());

        if (!session.isCurrentlyOpen()) {
            throw new IllegalArgumentException("Resumption window is closed or has expired");
        }

        cohortEnrollmentRepository.findByStudentAndCohort(student, session.getCohort())
                .filter(e -> e.isActive())
                .orElseThrow(() -> new IllegalArgumentException("You are not enrolled in this cohort"));

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