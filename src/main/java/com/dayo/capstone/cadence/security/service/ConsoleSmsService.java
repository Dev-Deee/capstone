package com.dayo.capstone.cadence.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Local-only SMS stub — always logs the OTP to the console, never
 * makes any real HTTP call to an SMS gateway. Swap this bean for a real
 * implementation (e.g. TermiiSmsService) once a provider is chosen,
 * without touching any caller.
 */
@Slf4j
@Service
public class ConsoleSmsService implements SmsService {

    @Override
    public void sendOtp(String phoneNumber, String code) {
        log.info("SMS content (always logged for local testing) — to={}, otp={}", phoneNumber, code);
    }

    @Override
    public void send(String phoneNumber, String message) {
        log.info("SMS content (always logged for local testing) — to={}, message={}", phoneNumber, message);
    }
}
