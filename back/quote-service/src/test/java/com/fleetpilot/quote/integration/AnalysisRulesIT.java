package com.fleetpilot.quote.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("Rules of the quote analysis, set by the administrators")
class AnalysisRulesIT extends IntegrationTestBase {

    private static final String RULES = """
            {"partTolerance": %s, "timeTolerance": 0.15, "rateTolerance": 0.10, "suppliesShare": 0.06,
             "suppliesMinimum": 15, "negotiationShare": 0.01, "negotiationMinimum": 5, "autoApprovalLimit": %s,
             "managerApprovalLimit": 5000, "version": %d}""";

    private int version() throws Exception {
        String body = mvc.perform(get("/api/v1/tariff/rules").with(admin())).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.parse(body).read("$.version");
    }

    @Test
    void the_garages_read_the_rules_and_only_an_administrator_changes_them() throws Exception {
        mvc.perform(get("/api/v1/tariff/rules").with(garage(PRADO))).andExpect(status().isOk())
                .andExpect(jsonPath("$.partTolerance").value(0.10))
                .andExpect(jsonPath("$.autoApprovalLimit").value(300.0));
        mvc.perform(put("/api/v1/tariff/rules").with(manager()).contentType(MediaType.APPLICATION_JSON)
                .content(RULES.formatted("0.10", "300", version()))).andExpect(status().isForbidden());
    }

    @Test
    void an_automatic_approval_above_the_delegation_is_refused_and_a_change_is_signed_then_restored() throws Exception {
        mvc.perform(put("/api/v1/tariff/rules").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(RULES.formatted("0.10", "9000", version())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AUTO_APPROVAL_ABOVE_LIMIT"));

        mvc.perform(put("/api/v1/tariff/rules").with(user("theo.lambert", "platform-admin",
                        java.util.Map.of("name", "Théo Lambert"))).contentType(MediaType.APPLICATION_JSON)
                        .content(RULES.formatted("0.12", "300", version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partTolerance").value(0.12))
                .andExpect(jsonPath("$.updatedBy").value("Théo Lambert"));

        // a stale version is refused: two administrators changing the rules at the same time
        mvc.perform(put("/api/v1/tariff/rules").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(RULES.formatted("0.10", "300", version() - 1))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/tariff/rules").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(RULES.formatted("0.10", "300", version()))).andExpect(status().isOk());
    }
}
