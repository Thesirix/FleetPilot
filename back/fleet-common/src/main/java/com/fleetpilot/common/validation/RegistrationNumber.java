package com.fleetpilot.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fleetpilot.common.util.RegistrationNumbers;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** The annotated string must be a valid French registration number (see {@link RegistrationNumbers}). Null is valid. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RegistrationNumber.Validator.class)
public @interface RegistrationNumber {

    String message() default "must be a valid French registration number (AB-123-CD)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Bean Validation implementation of {@link RegistrationNumber}. */
    class Validator implements ConstraintValidator<RegistrationNumber, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || RegistrationNumbers.isValid(value);
        }
    }
}
