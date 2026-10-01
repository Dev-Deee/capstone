package com.dayo.capstone.cadence.attendance.repository;

import com.dayo.capstone.cadence.attendance.model.AttendanceRecord;
import com.dayo.capstone.cadence.attendance.model.Session;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findBySessionAndStudent(Session session, User student);
    List<AttendanceRecord> findBySession(Session session);
}