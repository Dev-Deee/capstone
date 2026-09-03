package com.dayo.capstone.cadence.attendance.repository;

import com.dayo.capstone.cadence.attendance.model.ResumptionAttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.ResumptionSession;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumptionAttendanceRecordRepository extends JpaRepository<ResumptionAttendanceRecord, Long> {
    Optional<ResumptionAttendanceRecord> findByResumptionSessionAndStudent(
            ResumptionSession resumptionSession, User student);
}