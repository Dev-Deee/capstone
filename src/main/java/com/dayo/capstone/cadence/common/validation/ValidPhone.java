package com.dayo.capstone.cadence.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.*;

/** Phone numbers. Null and empty pass (optional fields); required fields add @NotBlank. See ValidationPatterns.PHONE. */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
// Empty passes too: optional phone fields arrive as "" from forms.
@Pattern(regexp = "^$|" + ValidationPatterns.PHONE)
@ReportAsSingleViolation
public @interface ValidPhone {
    String message() default ValidationPatterns.PHONE_MESSAGE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
