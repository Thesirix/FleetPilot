package com.fleetpilot.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fleetpilot.common.util.Vins;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** The annotated string must be a valid Vehicle Identification Number (see {@link Vins}). Null is valid. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Vin.Validator.class)
public @interface Vin {

    String message() default "must be a valid VIN (17 characters, no I, O or Q)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Bean Validation implementation of {@link Vin}. */
    class Validator implements ConstraintValidator<Vin, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Vins.isValid(value);
        }
    }
}
