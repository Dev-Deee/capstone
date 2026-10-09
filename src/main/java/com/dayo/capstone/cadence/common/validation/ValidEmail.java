package com.dayo.capstone.cadence.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.*;

/** Email addresses (required fields add @NotBlank). See ValidationPatterns.EMAIL. */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = ValidationPatterns.EMAIL)
@Size(max = 254)
@ReportAsSingleViolation
public @interface ValidEmail {
    String message() default ValidationPatterns.EMAIL_MESSAGE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
