package com.fleetpilot.vehicle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.IdentityDirectory;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("Administration: client companies, sites, cost centers, user accounts and the activity journal")
@Import(AdministrationIT.FakeDirectoryConfig.class)
class AdministrationIT extends IntegrationTestBase {

    private static final String NEOLIA = "474a1f7d-c59a-3321-a0a2-729ec42501d5";
    /** A site of Néolia with vehicles and drivers, and a driver of Néolia, from the deterministic demo data. */
    private static final String JEAN = "23f33fb7-f4c2-371c-93f9-e1f915b8bd15";

    /** Keycloak, replaced by accounts kept in memory: the rules are tested, not the HTTP calls. */
    static class FakeDirectory implements IdentityDirectory {
        final Map<String, Account> accounts = new LinkedHashMap<>();

        void reset() {
            accounts.clear();
            add(new Account("ldap-1", "claire.morel", "claire.morel@fleetpilot.dev", "Claire", "Morel", true,
                    Roles.FLEET_MANAGER, Map.of(), true, Instant.EPOCH));
            add(new Account("kc-1", "m.roux@neolia-energies.fr", "m.roux@neolia-energies.fr", "Mathilde", "Roux", true,
                    Roles.CLIENT_MANAGER, Map.of("company_id", NEOLIA), false, Instant.EPOCH));
        }

        void add(Account account) {
            accounts.put(account.id(), account);
        }

        @Override
        public List<Account> accounts() {
            return new ArrayList<>(accounts.values());
        }

        @Override
        public Optional<Account> account(String id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public List<Account> accountsWith(String attribute, String value) {
            return accounts.values().stream().filter(a -> value.equals(a.attributes().get(attribute))).toList();
        }

        @Override
        public String create(AccountData data, String temporaryPassword) {
            String id = UUID.randomUUID().toString();
            add(new Account(id, data.username(), data.email(), data.firstName(), data.lastName(), data.enabled(),
                    data.role(), data.attributes(), false, Instant.now()));
            return id;
        }

        @Override
        public void update(String id, AccountData data) {
            Account old = accounts.get(id);
            add(new Account(id, data.username(), data.email(), data.firstName(), data.lastName(), data.enabled(),
                    data.role() == null ? old.role() : data.role(), data.attributes(), false, old.createdAt()));
        }

        @Override
        public void resetPassword(String id, String temporaryPassword) {
            // nothing to keep
        }
    }

    @TestConfiguration
    static class FakeDirectoryConfig {
        @Bean
        @Primary
        FakeDirectory fakeDirectory() {
            return new FakeDirectory();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private FakeDirectory directory;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbc;

    @BeforeEach
    void resetDirectory() {
        directory.reset();
    }

    /** The other test classes share the database and count the demo companies: what this class creates goes away. */
    @org.junit.jupiter.api.AfterEach
    void removeTestCompanies() {
        String companies = "(select id from company where name like 'Cypress %')";
        jdbc.sql("delete from audit_entry where company_id in " + companies).update();
        jdbc.sql("delete from site where company_id in " + companies).update();
        jdbc.sql("delete from cost_center where company_id in " + companies).update();
        jdbc.sql("delete from company where name like 'Cypress %'").update();
    }

    private static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.parse(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).read(path);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor manager() {
        return user("claire.morel", Roles.FLEET_MANAGER, Map.of("name", "Claire Morel"));
    }

    private static RequestPostProcessor clientManager() {
        return user("m.roux@neolia-energies.fr", Roles.CLIENT_MANAGER, Map.of("company_id", NEOLIA));
    }

    @Test
    void a_company_is_created_with_its_sites_and_cost_centers_and_the_journal_tells_who_did_it() throws Exception {
        String siren = "99" + String.valueOf(System.nanoTime()).substring(0, 7);
        String id = read(send(post("/api/v1/admin/companies").with(manager()), """
                {"name": "Cypress Fret %s", "legalName": "Cypress Fret SAS", "siren": "%s", "segment": "SME",
                 "city": "Marseille", "postalCode": "13002", "contactEmail": "contact@example.test"}"""
                .formatted(siren, siren)).andExpect(status().isCreated()), "$.id");

        String siteId = read(send(post("/api/v1/admin/companies/{id}/sites", id).with(manager()), """
                {"name": "Marseille - La Joliette", "address": "10 place de la Joliette", "postalCode": "13002",
                 "city": "Marseille", "latitude": 43.3047, "longitude": 5.3667}""").andExpect(status().isCreated()), "$.id");
        send(post("/api/v1/admin/companies/{id}/cost-centers", id).with(manager()),
                "{\"code\": \"ops\", \"label\": \"Exploitation\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OPS"));
        send(post("/api/v1/admin/companies/{id}/cost-centers", id).with(manager()),
                "{\"code\": \"OPS\", \"label\": \"Doublon\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COST_CENTER_CODE_TAKEN"));

        mvc.perform(get("/api/v1/admin/companies/{id}", id).with(manager())).andExpect(status().isOk())
                .andExpect(jsonPath("$.sites").value(1)).andExpect(jsonPath("$.costCenters").value(1));
        mvc.perform(delete("/api/v1/admin/sites/{id}", siteId).with(manager())).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/audit").param("companyId", id).with(manager())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'SITE_DELETED')].actor").value(Matchers.hasItem("Claire Morel")))
                .andExpect(jsonPath("$.content[?(@.action == 'COMPANY_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action == 'COST_CENTER_CREATED')].targetLabel")
                        .value(Matchers.hasItem("OPS Exploitation")));
    }

    @Test
    void a_site_still_holding_vehicles_is_not_deleted() throws Exception {
        List<Map<String, Object>> sites = JsonPath.parse(mvc.perform(get("/api/v1/admin/companies/{id}/sites", NEOLIA)
                .with(manager())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).read("$");
        Map<String, Object> busy = sites.stream().filter(s -> ((Number) s.get("vehicles")).intValue() > 0).findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        String siteId = (String) ((Map<String, Object>) busy.get("site")).get("id");
        mvc.perform(delete("/api/v1/admin/sites/{id}", siteId).with(manager())).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SITE_IN_USE"));
    }

    @Test
    void the_journal_is_read_without_any_filter_and_a_client_manager_only_sees_their_company() throws Exception {
        mvc.perform(get("/api/v1/admin/audit").with(manager())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/audit").param("since", "2026-10-01T00:00:00Z").param("targetType", "VEHICLE")
                .param("actor", "claire").with(manager())).andExpect(status().isOk());
        List<String> companies = JsonPath.parse(mvc.perform(get("/api/v1/admin/audit").param("size", "200")
                .with(clientManager())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).read("$.content[*].companyId");
        assertThat(companies).isNotEmpty().allMatch(NEOLIA::equals);
    }

    @Test
    void a_client_company_is_not_administered_by_its_own_manager() throws Exception {
        mvc.perform(get("/api/v1/admin/companies").with(clientManager())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").with(clientManager())).andExpect(status().isForbidden());
    }

    @Test
    void an_administrator_creates_the_account_of_a_driver_attached_to_the_driver_once() throws Exception {
        String body = """
                {"email": "j.dupont.cy@example.test", "firstName": "Jean", "lastName": "Dupont", "role": "driver",
                 "companyId": "%s", "driverId": "%s", "enabled": true}""".formatted(NEOLIA, JEAN);
        ResultActions created = send(post("/api/v1/admin/users"), body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.driverName").value("Jean Dupont"))
                .andExpect(jsonPath("$.user.companyName").value("Néolia Énergies"));
        assertThat((String) read(created, "$.temporaryPassword")).hasSize(12).endsWith("!");

        send(post("/api/v1/admin/users"), body.replace("j.dupont.cy", "j.dupont.cy2")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_HAS_ACCOUNT"));
    }

    @Test
    void the_staff_accounts_of_the_directory_and_the_staff_roles_are_not_managed_here() throws Exception {
        send(put("/api/v1/admin/users/{id}", "ldap-1"), """
                {"email": "claire.morel@fleetpilot.dev", "firstName": "Claire", "lastName": "Morel",
                 "role": "client-manager", "companyId": "%s", "enabled": true}""".formatted(NEOLIA))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("USER_FROM_DIRECTORY"));
        send(post("/api/v1/admin/users"), """
                {"email": "chef.cy@example.test", "firstName": "Chef", "lastName": "Test", "role": "platform-admin",
                 "enabled": true}""").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_MANAGED"));
        send(post("/api/v1/admin/users").with(manager()), """
                {"email": "x.cy@example.test", "firstName": "X", "lastName": "Y", "role": "client-manager",
                 "companyId": "%s", "enabled": true}""".formatted(NEOLIA)).andExpect(status().isForbidden());
    }

    @Test
    void deactivating_a_company_disables_the_accounts_of_its_users() throws Exception {
        String siren = "98" + String.valueOf(System.nanoTime()).substring(0, 7);
        String id = read(send(post("/api/v1/admin/companies").with(manager()), """
                {"name": "Cypress Bus %s", "legalName": "Cypress Bus SA", "siren": "%s", "segment": "PUBLIC_SECTOR",
                 "city": "Aubagne"}""".formatted(siren, siren)).andExpect(status().isCreated()), "$.id");
        String account = read(send(post("/api/v1/admin/users"), """
                {"email": "bus.cy@example.test", "firstName": "Léa", "lastName": "Bus", "role": "client-manager",
                 "companyId": "%s", "enabled": true}""".formatted(id)).andExpect(status().isCreated()), "$.user.id");

        mvc.perform(post("/api/v1/admin/companies/{id}/activation", id).param("active", "false").with(manager()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(directory.accounts.get(account).enabled()).isFalse();
        send(post("/api/v1/admin/users"), """
                {"email": "bus2.cy@example.test", "firstName": "Paul", "lastName": "Bus", "role": "client-manager",
                 "companyId": "%s", "enabled": true}""".formatted(id)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
    }

    @Test
    void a_wrong_siren_is_explained_with_the_rule_that_failed() throws Exception {
        send(post("/api/v1/admin/companies").with(manager()), """
                {"name": "Cypress Erreur", "legalName": "X", "siren": "1234", "segment": "SME", "city": "Paris"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("siren"))
                .andExpect(jsonPath("$.errors[0].constraint").value("Pattern"));
    }
}
