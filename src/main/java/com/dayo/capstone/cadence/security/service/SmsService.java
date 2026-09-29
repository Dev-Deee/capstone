package com.dayo.capstone.cadence.security.service;

/**
 * Interface-first SMS abstraction, mirroring the FileStorageService
 * pattern — provider is a future, isolated swap. ConsoleSmsService is
 * the active default bean until a real provider (Termii, Africa's
 * Talking, etc.) is chosen and wired in.
 */
public interface SmsService {
    void sendOtp(String phoneNumber, String code);
}
