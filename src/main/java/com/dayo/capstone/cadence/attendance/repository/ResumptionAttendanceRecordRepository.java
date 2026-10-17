package com.dayo.capstone.cadence.attendance.repository;

import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumptionAttendanceRecordRepository extends JpaRepository<ResumptionAttendanceRecord, Long> {
    Optional<ResumptionAttendanceRecord> findByResumptionSessionAndStudent(
            ResumptionSession resumptionSession, User student);
    List<ResumptionAttendanceRecord> findByResumptionSession(ResumptionSession resumptionSession);
    Optional<ResumptionAttendanceRecord> findByPublicIdAndStudent(UUID publicId, User student);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM ResumptionAttendanceRecord r " +
        "WHERE r.student = :student AND r.resumptionSession.cohort = :cohort")
    List<ResumptionAttendanceRecord> findByStudentAndCohort(
            @org.springframework.data.repository.query.Param("student") User student,
            @org.springframework.data.repository.query.Param("cohort") com.dayo.capstone.cadence.cohort.model.Cohort cohort);
}