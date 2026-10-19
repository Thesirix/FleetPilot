package com.fleetpilot.gateway.resolver;

import static com.fleetpilot.gateway.resolver.FleetQueries.orNull;
import static com.fleetpilot.gateway.resolver.FleetQueries.pageParams;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.util.Map;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * The administration screens: companies, sites and cost centers and the activity journal (fleet service), user
 * accounts (fleet service, which talks to Keycloak), analysis rules (quote service), electric models and low emission
 * zones (energy service).
 */
@Controller
public class AdminResolvers {

    private final ServiceClient vehicles;
    private final ServiceClient quotes;
    private final ServiceClient energy;

    public AdminResolvers(ServiceClient vehicleService, ServiceClient quoteService, ServiceClient energyService) {
        this.vehicles = vehicleService;
        this.quotes = quoteService;
        this.energy = energyService;
    }

    // ---------------------------------------------------------------- companies

    @QueryMapping
    public Object adminCompanies() {
        return vehicles.getList("/api/v1/admin/companies");
    }

    @QueryMapping
    public Object adminCompany(@Argument String id) {
        return orNull(() -> vehicles.get("/api/v1/admin/companies/{id}", Map.of(), id));
    }

    @SchemaMapping(typeName = "AdminCompany", field = "siteUsage")
    public Object siteUsage(Map<String, Object> admin) {
        return vehicles.getList("/api/v1/admin/companies/{id}/sites", companyId(admin));
    }

    @SchemaMapping(typeName = "AdminCompany", field = "costCenterUsage")
    public Object costCenterUsage(Map<String, Object> admin) {
        return vehicles.getList("/api/v1/admin/companies/{id}/cost-centers", companyId(admin));
    }

    @MutationMapping
    public Object createCompany(@Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/admin/companies", input);
    }

    @MutationMapping
    public Object updateCompany(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/admin/companies/{id}", input, id);
    }

    @MutationMapping
    public Object setCompanyActive(@Argument String id, @Argument boolean active) {
        return vehicles.send(POST, "/api/v1/admin/companies/{id}/activation?active={active}", null, id, active);
    }

    @MutationMapping
    public Object createSite(@Argument String companyId, @Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/admin/companies/{id}/sites", input, companyId);
    }

    @MutationMapping
    public Object updateSite(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/admin/sites/{id}", input, id);
    }

    @MutationMapping
    public boolean deleteSite(@Argument String id) {
        vehicles.send(DELETE, "/api/v1/admin/sites/{id}", null, id);
        return true;
    }

    @MutationMapping
    public Object createCostCenter(@Argument String companyId, @Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/admin/companies/{id}/cost-centers", input, companyId);
    }

    @MutationMapping
    public Object updateCostCenter(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/admin/cost-centers/{id}", input, id);
    }

    @MutationMapping
    public boolean deleteCostCenter(@Argument String id) {
        vehicles.send(DELETE, "/api/v1/admin/cost-centers/{id}", null, id);
        return true;
    }

    @QueryMapping
    public Object auditTrail(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size) {
        return vehicles.get("/api/v1/admin/audit", pageParams(filter, page, size == null ? 30 : size, null));
    }

    // ---------------------------------------------------------------- users

    @QueryMapping
    public Object users(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size) {
        return vehicles.get("/api/v1/admin/users", pageParams(filter, page, size, null));
    }

    @QueryMapping
    public Object user(@Argument String id) {
        return orNull(() -> vehicles.get("/api/v1/admin/users/{id}", Map.of(), id));
    }

    @QueryMapping
    public Object lessors() {
        return vehicles.getList("/api/v1/admin/users/lessors");
    }

    /** The garage of a garage account, from the quote service; null when it no longer exists. */
    @SchemaMapping(typeName = "User", field = "garage")
    public Object garage(Map<String, Object> user) {
        Object id = user.get("garageId");
        return id == null ? null : orNull(() -> quotes.get("/api/v1/garages/{id}", Map.of(), id));
    }

    @MutationMapping
    public Object createUser(@Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/admin/users", input);
    }

    @MutationMapping
    public Object updateUser(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/admin/users/{id}", input, id);
    }

    @MutationMapping
    public Object resetUserPassword(@Argument String id) {
        return vehicles.send(POST, "/api/v1/admin/users/{id}/password", null, id).get("temporaryPassword");
    }

    // ---------------------------------------------------------------- rules and references

    @QueryMapping
    public Object analysisRules() {
        return quotes.get("/api/v1/tariff/rules", Map.of());
    }

    @MutationMapping
    public Object updateAnalysisRules(@Argument Map<String, Object> input) {
        return quotes.send(PUT, "/api/v1/tariff/rules", input);
    }

    @QueryMapping
    public Object lowEmissionZones() {
        return energy.getList("/api/v1/energy/zones");
    }

    @MutationMapping
    public Object saveEvModel(@Argument String code, @Argument Map<String, Object> input) {
        return energy.send(PUT, "/api/v1/energy/catalog/{code}", input, code).get("vehicles");
    }

    @MutationMapping
    public Object deleteEvModel(@Argument String code) {
        return energy.send(DELETE, "/api/v1/energy/catalog/{code}", null, code).get("vehicles");
    }

    @MutationMapping
    public Object saveLowEmissionZone(@Argument String city, @Argument Map<String, Object> input) {
        return energy.send(PUT, "/api/v1/energy/zones/{city}", input, city).get("vehicles");
    }

    @MutationMapping
    public Object deleteLowEmissionZone(@Argument String city) {
        return energy.send(DELETE, "/api/v1/energy/zones/{city}", null, city).get("vehicles");
    }

    @SuppressWarnings("unchecked")
    private static Object companyId(Map<String, Object> admin) {
        return ((Map<String, Object>) admin.get("company")).get("id");
    }
}
