package com.fleetpilot.vehicle.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.security.KeycloakJwtConverter;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.ReferenceDataService;
import com.fleetpilot.vehicle.application.VehicleService;
import com.fleetpilot.vehicle.application.view.Views.CompanyView;
import com.fleetpilot.vehicle.application.view.Views.VehicleSummary;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.VehicleCriteria;
import io.cucumber.java.After;
import io.cucumber.java.fr.Alors;
import io.cucumber.java.fr.Quand;
import io.cucumber.java.fr.Soit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Steps that run as a given user, with a token like the ones issued by the Keycloak realm. */
public class IsolationSteps {

    @Autowired
    private ReferenceDataService referenceData;
    @Autowired
    private VehicleService vehicles;

    private Map<String, UUID> companies;
    private List<VehicleSummary> visible;
    private Exception lastError;

    @After
    public void logout() {
        SecurityContextHolder.clearContext();
    }

    @Soit("(que )je suis connecté en tant que gestionnaire de {string}")
    public void connecte_gestionnaire_client(String company) {
        login("gestionnaire", Roles.CLIENT_MANAGER, Map.of("company_id", companyId(company).toString()));
    }

    @Soit("(que )je suis connecté en tant que gestionnaire de flotte FleetPilot")
    public void connecte_gestionnaire_fleetpilot() {
        login("claire.morel", Roles.FLEET_MANAGER, Map.of());
    }

    @Quand("je consulte la liste des véhicules")
    public void je_consulte_la_liste() {
        visible = vehicles.search(new VehicleCriteria(null, null, null, null, null, null, null), PageRequest.of(0, 2_000))
                .getContent();
    }

    @Quand("je consulte un véhicule de {string}")
    public void je_consulte_un_vehicule_de(String company) {
        UUID otherCompany = companyId(company);
        UUID vehicle = asSystem(() -> vehicles.search(new VehicleCriteria(otherCompany, null, null, null, null, null,
                null), PageRequest.of(0, 1)).getContent().getFirst().id());
        try {
            vehicles.get(vehicle);
            lastError = null;
        } catch (ResourceNotFoundException e) {
            lastError = e;
        }
    }

    @Alors("je ne vois que des véhicules de {string}")
    public void je_ne_vois_que(String company) {
        assertThat(visible).isNotEmpty();
        assertThat(visible).extracting(VehicleSummary::companyId).containsOnly(companyId(company));
    }

    @Alors("je vois des véhicules de {string}, {string} et {string}")
    public void je_vois_des_vehicules_de(String first, String second, String third) {
        Set<UUID> seen = visible.stream().map(VehicleSummary::companyId).collect(Collectors.toSet());
        assertThat(seen).contains(companyId(first), companyId(second), companyId(third));
    }

    @Alors("le véhicule est introuvable")
    public void le_vehicule_est_introuvable() {
        assertThat(lastError).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "VEHICLE_NOT_FOUND");
    }

    private UUID companyId(String name) {
        if (companies == null) {
            companies = asSystem(() -> referenceData.companies().stream()
                    .collect(Collectors.toMap(CompanyView::name, CompanyView::id)));
        }
        return companies.get(name);
    }

    /** Runs a lookup with the rights of the system, to prepare a step without the user's restrictions. */
    private static <T> T asSystem(Supplier<T> lookup) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.clearContext();
        try {
            return lookup.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(current);
        }
    }

    private static void login(String username, String role, Map<String, Object> claims) {
        Jwt.Builder token = Jwt.withTokenValue("acceptance-test").header("alg", "RS256").subject(username)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", List.of(role)));
        claims.forEach(token::claim);
        SecurityContextHolder.getContext().setAuthentication(new KeycloakJwtConverter().convert(token.build()));
    }
}
