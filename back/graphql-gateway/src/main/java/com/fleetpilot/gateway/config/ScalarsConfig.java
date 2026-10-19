package com.fleetpilot.gateway.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * {@code Date} and {@code DateTime} scalars. Values travel as ISO 8601 strings, exactly as the REST services produce and
 * expect them; inputs are validated so that a malformed date is a GraphQL validation error, not a service error.
 */
@Configuration
public class ScalarsConfig {

    @Bean
    public RuntimeWiringConfigurer scalars() {
        return wiring -> wiring
                .scalar(isoScalar("Date", "Calendar date, ISO 8601", LocalDate::parse))
                .scalar(isoScalar("DateTime", "Instant, ISO 8601 in UTC", Instant::parse));
    }

    static GraphQLScalarType isoScalar(String name, String description, Consumer<String> validator) {
        return GraphQLScalarType.newScalar()
                .name(name)
                .description(description)
                .coercing(new Coercing<String, String>() {
                    @Override
                    public String serialize(Object value, GraphQLContext context, Locale locale) {
                        return value.toString();
                    }

                    @Override
                    public String parseValue(Object input, GraphQLContext context, Locale locale) {
                        return validated(input.toString(), CoercingParseValueException::new);
                    }

                    @Override
                    public String parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context,
                            Locale locale) {
                        if (input instanceof StringValue string) {
                            return validated(string.getValue(), CoercingParseLiteralException::new);
                        }
                        throw new CoercingParseLiteralException(name + " must be a string");
                    }

                    private <E extends RuntimeException> String validated(String value,
                            Function<String, E> error) {
                        try {
                            validator.accept(value);
                            return value;
                        } catch (DateTimeParseException e) {
                            throw error.apply("Invalid " + name + ": " + value);
                        }
                    }
                })
                .build();
    }
}
