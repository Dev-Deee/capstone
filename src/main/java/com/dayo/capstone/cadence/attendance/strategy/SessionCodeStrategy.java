package com.dayo.capstone.cadence.attendance.strategy;

import com.dayo.capstone.cadence.attendance.model.Session;
import org.springframework.stereotype.Component;

@Component
public class SessionCodeStrategy implements AttendanceVerificationStrategy {

    @Override
    public boolean verify(Session session, String submittedValue) {
        return session.isCurrentlyOpen()
                && session.getSessionCode().equalsIgnoreCase(submittedValue);
    }
}