package com.fleetpilot.reporting.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fleetpilot.common.messaging.Queues;
import com.fleetpilot.reporting.unit.FakeSources;
import com.jayway.jsonpath.JsonPath;
import jakarta.jms.TextMessage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.activemq.ActiveMQContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A report requested through the API is built in the background from the ActiveMQ queue, then downloaded. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReportFlowIT.Stubs.class)
@DisplayName("Reports, from the request to the download")
class ReportFlowIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @ServiceConnection
    static final ActiveMQContainer ACTIVEMQ = new ActiveMQContainer("apache/activemq-classic:6.2.0");

    static {
        POSTGRES.start();
        ACTIVEMQ.start();
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JmsTemplate jms;

    static RequestPostProcessor clientManager() {
        return jwt().jwt(token -> token.claim("preferred_username", "m.roux@neolia-energies.fr")
                        .claim("email", "m.roux@neolia-energies.fr").claim("name", "Mathilde Roux")
                        .claim("company_id", FakeSources.COMPANY.toString())
                        .claim("realm_access", Map.of("roles", List.of("client-manager"))))
                .authorities(new SimpleGrantedAuthority("ROLE_client-manager"));
    }

    static RequestPostProcessor otherManager() {
        return jwt().jwt(token -> token.claim("preferred_username", "p.bianchi@azur-provence-metropole.fr")
                        .claim("realm_access", Map.of("roles", List.of("client-manager"))))
                .authorities(new SimpleGrantedAuthority("ROLE_client-manager"));
    }

    @Test
    void a_report_is_built_in_the_background_downloaded_and_announced_by_e_mail() throws Exception {
        String json = mvc.perform(post("/api/v1/reports").with(clientManager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TCO\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.companyId").value(FakeSources.COMPANY.toString()))
                .andExpect(jsonPath("$.title").value("Coût total de détention (TCO) · Néolia Énergies"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        String ready = waitForStatus(id, "READY");
        assertThat(JsonPath.<Integer>read(ready, "$.rowCount")).isEqualTo(3);
        assertThat(JsonPath.<String>read(ready, "$.fileName")).endsWith(".xlsx");

        byte[] file = mvc.perform(get("/api/v1/reports/" + id + "/file").with(clientManager()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getSheet("TCO par véhicule")).isNotNull();
        }

        jms.setReceiveTimeout(10_000);
        TextMessage email = (TextMessage) jms.receive(Queues.NOTIFICATIONS);
        assertThat(email).isNotNull();
        assertThat(email.getText()).contains("\"template\":\"report-ready\"", "m.roux@neolia-energies.fr");

        mvc.perform(get("/api/v1/reports").with(clientManager()))
                .andExpect(jsonPath("$.content[*].id", hasItem(id)));
    }

    @Test
    void the_report_of_someone_else_does_not_exist_for_me() throws Exception {
        String json = mvc.perform(post("/api/v1/reports").with(clientManager()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FLEET_INVENTORY\"}")).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        mvc.perform(get("/api/v1/reports/" + id).with(otherManager())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/" + id + "/file").with(otherManager())).andExpect(status().isNotFound());
    }

    @Test
    void a_periodic_report_without_period_is_refused() throws Exception {
        mvc.perform(post("/api/v1/reports").with(clientManager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"QUOTES\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERIOD_REQUIRED"));
    }

    @Test
    void indicators_and_catalog_are_available_to_managers() throws Exception {
        mvc.perform(get("/api/v1/indicators").with(clientManager()))
                .andExpect(jsonPath("$.vehicles").value(3))
                .andExpect(jsonPath("$.totalCost").value(11000.0));
        mvc.perform(get("/api/v1/reports/types").with(clientManager()))
                .andExpect(jsonPath("$[0].type").value("FLEET_INVENTORY"))
                .andExpect(jsonPath("$[4].format").value("PDF"));
    }

    private String waitForStatus(String id, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            String json = mvc.perform(get("/api/v1/reports/" + id).with(clientManager())).andReturn().getResponse()
                    .getContentAsString();
            if (expected.equals(JsonPath.read(json, "$.status"))) {
                return json;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Report " + id + " never became " + expected);
    }

    @TestConfiguration
    static class Stubs {

        /** Stands for both the fleet and the quote services. */
        @Bean
        @Primary
        FakeSources fakeSources() {
            return new FakeSources();
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FakeSources.NOW, ZoneId.of("Europe/Paris"));
        }
    }
}
