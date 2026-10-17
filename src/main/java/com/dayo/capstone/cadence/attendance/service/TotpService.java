package com.dayo.capstone.cadence.attendance.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * RFC 6238 TOTP implementation — no external dependencies, pure JDK.
 *
 * Used for rotating 6-digit attendance codes on both Session (class attendance)
 * and ResumptionSession (daily campus check-in). Period is always 30 s.
 *
 * Validation window: current counter + previous counter (up to 60 s total),
 * so a student who copies the code just before it rotates still gets through.
 */
@Service
public class TotpService {

    public static final long PERIOD_SECONDS = 30L;

    /**
     * Generates a fresh TOTP secret: 20 bytes of SecureRandom, hex-encoded.
     * Call once when a session is opened; never regenerate on code-refresh.
     */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        StringBuilder hex = new StringBuilder(40);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * Computes the current 6-digit TOTP code for the given secret.
     * Do NOT cache this — call fresh per request.
     */
    public String currentCode(String secretHex, long periodSeconds) {
        long counter = Instant.now().getEpochSecond() / periodSeconds;
        return hotpCode(secretHex, counter);
    }

    /**
     * Returns true if submittedCode matches the current or immediately preceding
     * TOTP window (tolerates up to one full period of typing lag).
     */
    public boolean isValid(String secretHex, String submittedCode, long periodSeconds) {
        if (secretHex == null || submittedCode == null) {
            return false;
        }
        long counter = Instant.now().getEpochSecond() / periodSeconds;
        return hotpCode(secretHex, counter).equals(submittedCode)
                || hotpCode(secretHex, counter - 1).equals(submittedCode);
    }

    /**
     * Seconds until the current window expires — drives the facilitator's countdown.
     */
    public long secondsRemainingInWindow(long periodSeconds) {
        return periodSeconds - (Instant.now().getEpochSecond() % periodSeconds);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String hotpCode(String secretHex, long counter) {
        byte[] secretBytes = hexToBytes(secretHex);

        // Counter as big-endian 8-byte array (RFC 4226 §5.2)
        byte[] counterBytes = new byte[8];
        long c = counter;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (c & 0xff);
            c >>= 8;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);

            // Dynamic truncation (RFC 4226 §5.3)
            int offset = hash[hash.length - 1] & 0x0f;
            int truncated = ((hash[offset]     & 0x7f) << 24)
                          | ((hash[offset + 1] & 0xff) << 16)
                          | ((hash[offset + 2] & 0xff) << 8)
                          |  (hash[offset + 3] & 0xff);

            return String.format("%06d", truncated % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP computation failed", e);
        }
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                  + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }
}
