package com.dayo.capstone.cadence.common.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The three shared rules, on representative good and bad input. */
class ValidationPatternsTest {

    @ParameterizedTest
    @ValueSource(strings = {"ada@example.com", "a.b+tag@mail.example.co.uk", "first_last@sub-domain.io", "x@y.ng", "UPPER@EXAMPLE.COM"})
    void acceptsEmails(String email) {
        assertThat(ValidationPatterns.isEmail(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ada", "ada@", "@example.com", "ada@localhost", "ada@example", "ada@example.c",
            "ada @example.com", "ada@exa mple.com", "ada@-example.com", "ada@example-.com", "ada@@example.com", "ada@example..com"})
    void rejectsEmails(String email) {
        assertThat(ValidationPatterns.isEmail(email)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+2348012345678", "+14155550123", "+447911123456", "08012345678", "0801234567"})
    void acceptsPhones(String phone) {
        assertThat(ValidationPatterns.isPhone(phone)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8012345678", "+0801234567", "+234801", "+2348012345678901234", "080123", "080-123-4567",
            "+234 801 234 5678", "phone", "080123456789"})
    void rejectsPhones(String phone) {
        assertThat(ValidationPatterns.isPhone(phone)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Test-Password-1!", "Short1!A", "Correct Horse 9 battery"})
    void acceptsPasswords(String password) {
        assertThat(password.matches(ValidationPatterns.PASSWORD)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alllowercase1!", "ALLUPPERCASE1!", "NoDigits!!", "NoSymbols123", "Ab1!"})
    void rejectsPasswords(String password) {
        assertThat(password.matches(ValidationPatterns.PASSWORD)).isFalse();
    }
}
