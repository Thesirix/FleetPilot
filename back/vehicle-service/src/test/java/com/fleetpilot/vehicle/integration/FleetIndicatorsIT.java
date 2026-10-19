package com.fleetpilot.vehicle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Dashboard indicators, demo data, sweep and CSV import")
class FleetIndicatorsIT extends IntegrationTestBase {

    private int number(ResultActions result, String path) throws Exception {
        Number value = JsonPath.parse(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).read(path);
        return value.intValue();
    }

    private String companyId(String segment) throws Exception {
        return JsonPath.parse(mvc.perform(get("/api/v1/companies")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).<List<String>>read("$[?(@.segment == '" + segment + "')].id")
                .getFirst();
    }

    @Test
    void the_demo_data_is_loaded_at_startup() throws Exception {
        mvc.perform(get("/api/v1/companies"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].name").value(hasItem("Néolia Énergies")));
        assertThat(number(mvc.perform(get("/api/v1/drivers?size=1")), "$.totalElements")).isGreaterThanOrEqualTo(1_200);
    }

    @Test
    void sql_indicators_match_the_domain_rules_used_by_the_lists() throws Exception {
        ResultActions summary = mvc.perform(get("/api/v1/fleet/summary")).andExpect(status().isOk());

        assertThat(number(summary, "$.overdueDeadlines"))
                .isEqualTo(number(mvc.perform(get("/api/v1/deadlines?urgency=OVERDUE&size=1")), "$.totalElements"))
                .isPositive();
        assertThat(number(summary, "$.dueSoonDeadlines"))
                .isEqualTo(number(mvc.perform(get("/api/v1/deadlines?urgency=DUE_SOON&size=1")), "$.totalElements"))
                .isPositive();
        assertThat(number(summary, "$.openIncidents"))
                .isEqualTo(number(mvc.perform(get("/api/v1/incidents?open=true&size=1")), "$.totalElements"));
        assertThat(number(summary, "$.contractsEndingSoon"))
                .isEqualTo(number(mvc.perform(get("/api/v1/contracts?status=ACTIVE&endingBefore=2027-01-12&size=1")),
                        "$.totalElements"));
        assertThat(number(summary, "$.assignedVehicles"))
                .isEqualTo(number(mvc.perform(get("/api/v1/vehicles?assigned=true&size=1")), "$.totalElements"));
        assertThat(number(summary, "$.contractsOverrunRisk")).isPositive();
        assertThat(number(summary, "$.byCritAir.0")).as("electric vehicles").isPositive();
    }

    @Test
    void indicators_can_be_scoped_to_one_company() throws Exception {
        String company = companyId("SME");
        int all = number(mvc.perform(get("/api/v1/fleet/summary")), "$.inFleet");
        int sme = number(mvc.perform(get("/api/v1/fleet/summary?companyId=" + company)), "$.inFleet");

        assertThat(sme).isPositive().isLessThan(all);
        assertThat(number(mvc.perform(get("/api/v1/fleet/summary?companyId=" + company)), "$.byCategory.TRUCK"))
                .as("the transport SME runs trucks").isPositive();
    }

    @Test
    void the_daily_sweep_sends_each_reminder_once() throws Exception {
        ResultActions first = mvc.perform(post("/api/v1/fleet/sweep")).andExpect(status().isOk());
        assertThat(number(first, "$.deadlineAlerts")).isPositive();
        assertThat(number(first, "$.overrunAlerts")).isPositive();

        mvc.perform(post("/api/v1/fleet/sweep"))
                .andExpect(jsonPath("$.deadlineAlerts").value(0))
                .andExpect(jsonPath("$.overrunAlerts").value(0));
    }

    @Test
    void a_csv_import_creates_valid_lines_and_reports_the_others() throws Exception {
        String company = companyId("PUBLIC_SECTOR");
        String csv = """
                registration;vin;category;make;model;trim;energy;crit_air;co2;fiscal_power;first_registration;acquisition;mileage;site
                ZJ-101-AB;VF1ZZ000000000101;VU;Renault;Kangoo Van E-Tech;;Électrique;E;0;4;15/03/2025;Achat;8250;Aubagne - Dépôt des Paluds
                ZJ-102-AB;VF1ZZ000000000102;VL;Peugeot;208;;Essence;1;121;5;2024-06-01;LLD;21000;
                ZJ-103-AB;VF1ZZ000000000103;Moto;Yamaha;MT-07;;Essence;1;110;;2024-06-01;Achat;1000;
                AB-000-CD;VF1ZZ000000000104;VL;Peugeot;208;;Essence;1;121;5;2024-06-01;LLD;21000;Nowhere
                """;
        MockMultipartFile file = new MockMultipartFile("file", "vehicules.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/vehicles/import").file(file).param("companyId", company))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.rejected[0].line").value(4))
                .andExpect(jsonPath("$.rejected[0].errors[0]").value("category: invalid value \"Moto\""))
                .andExpect(jsonPath("$.rejected[1].line").value(5))
                .andExpect(jsonPath("$.rejected[1].errors.length()").value(2));

        mvc.perform(get("/api/v1/vehicles/by-registration/ZJ-101-AB"))
                .andExpect(jsonPath("$.energy").value("ELECTRIC"))
                .andExpect(jsonPath("$.site.name").value("Aubagne - Dépôt des Paluds"));
    }
}
