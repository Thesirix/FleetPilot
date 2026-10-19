package com.fleetpilot.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fleetpilot.common.validation.RegistrationNumber;
import com.fleetpilot.common.validation.Vin;
import com.fleetpilot.common.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private MockMvc mvc;

    @RestController
    static class SampleController {

        record VehicleRequest(@NotBlank @RegistrationNumber String registration, @Vin String vin) {
        }

        @GetMapping("/vehicles/{id}")
        String find(@PathVariable long id) {
            throw new ResourceNotFoundException("Vehicle", id);
        }

        @GetMapping("/vehicles/{id}/assign")
        String assign(@PathVariable long id) {
            throw new BusinessRuleException("VEHICLE_IN_REPAIR", "Vehicle " + id + " is in repair and cannot be assigned");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @PostMapping("/vehicles")
        String create(@Valid @RequestBody VehicleRequest request) {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void not_found_is_a_problem_detail_with_a_stable_code() throws Exception {
        mvc.perform(get("/vehicles/42").header(CorrelationIdFilter.HEADER, "test-correlation-0001"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-correlation-0001"))
                .andExpect(jsonPath("$.type").value("https://fleetpilot.dev/problems/vehicle-not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Vehicle 42 not found"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-0001"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void business_rule_violations_are_422() throws Exception {
        mvc.perform(get("/vehicles/7/assign"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VEHICLE_IN_REPAIR"));
    }

    @Test
    void invalid_body_lists_every_field_error() throws Exception {
        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"AB-000-CD\",\"vin\":\"TOO-SHORT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("registration"))
                .andExpect(jsonPath("$.errors[1].field").value("vin"));
    }

    @Test
    void malformed_json_keeps_the_framework_status_with_our_shape() throws Exception {
        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void wrong_value_type_names_the_field() throws Exception {
        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"AB-123-CD\",\"vin\":[1,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("vin"));
    }

    @Test
    void unexpected_errors_never_leak_internal_details() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("hunter2"))));
    }
}
