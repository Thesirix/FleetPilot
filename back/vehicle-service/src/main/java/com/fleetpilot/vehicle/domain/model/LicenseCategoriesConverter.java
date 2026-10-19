package com.fleetpilot.vehicle.domain.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a set of licence categories as a sorted, comma separated string ("B,C,CE"). */
@Converter
public class LicenseCategoriesConverter implements AttributeConverter<Set<LicenseCategory>, String> {

    @Override
    public String convertToDatabaseColumn(Set<LicenseCategory> categories) {
        return categories.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<LicenseCategory> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.noneOf(LicenseCategory.class);
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(LicenseCategory::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(LicenseCategory.class)));
    }
}
