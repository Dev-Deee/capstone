package com.dayo.capstone.cadence.common.validation;

import java.util.regex.Pattern;

/**
 * The one definition of what a valid email, phone number and new password
 * look like. Used by the @ValidEmail / @ValidPhone / @StrongPassword request
 * annotations and by services that validate lists themselves. The web app
 * mirrors these in lib/validation.ts — change both together.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {}

    /**
     * local@domain.tld — letters, digits and . _ % + - before the @; domain
     * labels of letters, digits and inner hyphens; a 2+ letter ending.
     */
    public static final String EMAIL =
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$";

    /**
     * International (+ then 8–15 digits, e.g. +2348012345678) or local
     * (a leading 0 and 10–11 digits, e.g. 08012345678) — local is how most
     * numbers on file were entered. Digits only: forms strip spaces and dashes.
     */
    public static final String PHONE = "^(?:\\+[1-9][0-9]{7,14}|0[0-9]{9,10})$";

    /** 8–128 characters with at least one lowercase, uppercase, digit and symbol. */
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,128}$";

    public static final String EMAIL_MESSAGE = "Enter a valid email address, like name@example.com";
    public static final String PHONE_MESSAGE =
            "Enter a valid phone number: international like +2348012345678, or local like 08012345678";
    public static final String PASSWORD_MESSAGE =
            "Password must be 8–128 characters with an uppercase letter, a lowercase letter, a number and a symbol";

    private static final Pattern EMAIL_RE = Pattern.compile(EMAIL);
    private static final Pattern PHONE_RE = Pattern.compile(PHONE);

    public static boolean isEmail(String s) {
        return s != null && s.length() <= 254 && EMAIL_RE.matcher(s).matches();
    }

    public static boolean isPhone(String s) {
        return s != null && PHONE_RE.matcher(s).matches();
    }
}
