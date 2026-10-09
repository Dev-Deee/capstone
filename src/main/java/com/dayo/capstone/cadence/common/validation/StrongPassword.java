package com.dayo.capstone.cadence.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.*;

/** A password being set or changed. Never used on sign-in, so existing passwords keep working. See ValidationPatterns.PASSWORD. */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = ValidationPatterns.PASSWORD)
@ReportAsSingleViolation
public @interface StrongPassword {
    String message() default ValidationPatterns.PASSWORD_MESSAGE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
