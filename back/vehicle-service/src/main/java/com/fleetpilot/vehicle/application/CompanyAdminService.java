package com.fleetpilot.vehicle.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ConflictException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.vehicle.application.command.AdminCommands.SaveCompany;
import com.fleetpilot.vehicle.application.command.AdminCommands.SaveCostCenter;
import com.fleetpilot.vehicle.application.command.AdminCommands.SaveSite;
import com.fleetpilot.vehicle.application.view.Views.CompanyAdminView;
import com.fleetpilot.vehicle.application.view.Views.CompanyView;
import com.fleetpilot.vehicle.application.view.Views.CostCenterAdminView;
import com.fleetpilot.vehicle.application.view.Views.CostCenterView;
import com.fleetpilot.vehicle.application.view.Views.SiteAdminView;
import com.fleetpilot.vehicle.application.view.Views.SiteView;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.CostCenter;
import com.fleetpilot.vehicle.domain.model.Site;
import com.fleetpilot.vehicle.domain.repository.CompanyRepository;
import com.fleetpilot.vehicle.domain.repository.CostCenterRepository;
import com.fleetpilot.vehicle.domain.repository.SiteRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration of the client companies by the FleetPilot staff: their details, their sites and their cost centers. A
 * site or a cost center still in use cannot be deleted, so no vehicle or driver is left pointing to nothing.
 */
@Service
@Transactional
public class CompanyAdminService {

    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final CostCenterRepository costCenters;
    private final JdbcClient jdbc;
    private final AuditLog audit;
    private final IdentityDirectory directory;

    public CompanyAdminService(CompanyRepository companies, SiteRepository sites, CostCenterRepository costCenters,
            JdbcClient jdbc, AuditLog audit, IdentityDirectory directory) {
        this.directory = directory;
        this.companies = companies;
        this.sites = sites;
        this.costCenters = costCenters;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- companies

    @Transactional(readOnly = true)
    public List<CompanyAdminView> companies() {
        Map<UUID, long[]> counts = jdbc.sql("""
                select c.id,
                       (select count(*) from vehicle v where v.company_id = c.id),
                       (select count(*) from vehicle v where v.company_id = c.id and v.status <> 'DISPOSED'),
                       (select count(*) from driver d where d.company_id = c.id and d.active),
                       (select count(*) from site s where s.company_id = c.id),
                       (select count(*) from cost_center cc where cc.company_id = c.id),
                       (select count(*) from lease_contract l join vehicle v on v.id = l.vehicle_id
                         where v.company_id = c.id and l.status = 'ACTIVE')
                from company c""")
                .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), new long[] {rs.getLong(2), rs.getLong(3),
                    rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)}))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return companies.findAllByOrderByNameAsc().stream()
                .map(c -> {
                    long[] n = counts.getOrDefault(c.getId(), new long[6]);
                    return new CompanyAdminView(CompanyView.of(c), n[0], n[1], n[2], n[3], n[4], n[5]);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyAdminView company(UUID id) {
        load(id);
        return companies().stream().filter(c -> c.company().id().equals(id)).findFirst().orElseThrow();
    }

    public CompanyView create(SaveCompany cmd) {
        checkUnique(cmd, null);
        Company company = new Company(UUID.randomUUID(), cmd.name().trim(), cmd.legalName().trim(), cmd.siren(),
                cmd.segment(), cmd.city().trim());
        apply(company, cmd);
        companies.saveAndFlush(company);
        audit.record("COMPANY_CREATED", "COMPANY", company.getId(), company.getName(), company.getId(),
                "SIREN " + company.getSiren());
        return CompanyView.of(company);
    }

    public CompanyView update(UUID id, SaveCompany cmd) {
        Company company = load(id);
        checkVersion(cmd.version(), company.getVersion(), "Company", id);
        checkUnique(cmd, id);
        apply(company, cmd);
        companies.saveAndFlush(company);
        audit.record("COMPANY_UPDATED", "COMPANY", id, company.getName(), id, null);
        return CompanyView.of(company);
    }

    /**
     * Deactivating keeps the history of the company and disables the accounts of its client managers and drivers.
     * Reactivating does not enable them again: the administrator decides account by account.
     */
    public CompanyView setActive(UUID id, boolean active) {
        Company company = load(id);
        if (company.isActive() != active) {
            company.setActive(active);
            int disabled = 0;
            if (!active) {
                for (IdentityDirectory.Account account : directory.accountsWith("company_id", id.toString())) {
                    if (account.enabled() && !account.federated()) {
                        directory.update(account.id(), new IdentityDirectory.AccountData(account.username(),
                                account.email(), account.firstName(), account.lastName(), false,
                                account.role() == null ? directoryRole(account) : account.role(), account.attributes()));
                        disabled++;
                    }
                }
            }
            audit.record(active ? "COMPANY_ACTIVATED" : "COMPANY_DEACTIVATED", "COMPANY", id, company.getName(), id,
                    active ? null : disabled + " accounts disabled");
        }
        return CompanyView.of(company);
    }

    // ---------------------------------------------------------------- sites

    @Transactional(readOnly = true)
    public List<SiteAdminView> sites(UUID companyId) {
        load(companyId);
        Map<UUID, long[]> counts = counts("""
                select s.id, (select count(*) from vehicle v where v.site_id = s.id and v.status <> 'DISPOSED'),
                       (select count(*) from driver d where d.site_id = s.id and d.active)
                from site s where s.company_id = :company""", companyId);
        return sites.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(s -> {
                    long[] n = counts.getOrDefault(s.getId(), new long[2]);
                    return new SiteAdminView(SiteView.of(s), n[0], n[1], s.getVersion());
                })
                .toList();
    }

    public SiteView createSite(UUID companyId, SaveSite cmd) {
        Company company = load(companyId);
        checkSiteName(companyId, cmd.name(), null);
        Site site = new Site(UUID.randomUUID(), company, cmd.name().trim(), cmd.address().trim(), cmd.postalCode(),
                cmd.city().trim(), cmd.latitude(), cmd.longitude());
        sites.saveAndFlush(site);
        audit.record("SITE_CREATED", "SITE", site.getId(), site.getName(), companyId, company.getName());
        return SiteView.of(site);
    }

    public SiteView updateSite(UUID id, SaveSite cmd) {
        Site site = sites.findById(id).orElseThrow(() -> new ResourceNotFoundException("Site", id));
        checkVersion(cmd.version(), site.getVersion(), "Site", id);
        checkSiteName(site.getCompany().getId(), cmd.name(), id);
        site.update(cmd.name().trim(), cmd.address().trim(), cmd.postalCode(), cmd.city().trim(), cmd.latitude(),
                cmd.longitude());
        sites.saveAndFlush(site);
        audit.record("SITE_UPDATED", "SITE", id, site.getName(), site.getCompany().getId(), null);
        return SiteView.of(site);
    }

    public void deleteSite(UUID id) {
        Site site = sites.findById(id).orElseThrow(() -> new ResourceNotFoundException("Site", id));
        long used = jdbc.sql("select (select count(*) from vehicle where site_id = :id) + (select count(*) from driver "
                + "where site_id = :id)").param("id", id).query(Long.class).single();
        if (used > 0) {
            throw new BusinessRuleException("SITE_IN_USE",
                    "Site " + site.getName() + " still has " + used + " vehicles or drivers: move them first");
        }
        sites.delete(site);
        audit.record("SITE_DELETED", "SITE", id, site.getName(), site.getCompany().getId(), null);
    }

    // ---------------------------------------------------------------- cost centers

    @Transactional(readOnly = true)
    public List<CostCenterAdminView> costCenters(UUID companyId) {
        load(companyId);
        Map<UUID, long[]> counts = counts("""
                select cc.id, (select count(*) from vehicle v where v.cost_center_id = cc.id and v.status <> 'DISPOSED'), 0
                from cost_center cc where cc.company_id = :company""", companyId);
        return costCenters.findByCompanyIdOrderByCodeAsc(companyId).stream()
                .map(c -> new CostCenterAdminView(CostCenterView.of(c), counts.getOrDefault(c.getId(), new long[2])[0],
                        c.getVersion()))
                .toList();
    }

    public CostCenterView createCostCenter(UUID companyId, SaveCostCenter cmd) {
        Company company = load(companyId);
        checkCode(companyId, cmd.code(), null);
        CostCenter costCenter = new CostCenter(UUID.randomUUID(), company, cmd.code().trim().toUpperCase(),
                cmd.label().trim());
        costCenters.saveAndFlush(costCenter);
        audit.record("COST_CENTER_CREATED", "COST_CENTER", costCenter.getId(),
                costCenter.getCode() + " " + costCenter.getLabel(), companyId, company.getName());
        return CostCenterView.of(costCenter);
    }

    public CostCenterView updateCostCenter(UUID id, SaveCostCenter cmd) {
        CostCenter costCenter = costCenters.findById(id).orElseThrow(() -> new ResourceNotFoundException("CostCenter", id));
        checkVersion(cmd.version(), costCenter.getVersion(), "CostCenter", id);
        checkCode(costCenter.getCompany().getId(), cmd.code(), id);
        costCenter.update(cmd.code().trim().toUpperCase(), cmd.label().trim());
        costCenters.saveAndFlush(costCenter);
        audit.record("COST_CENTER_UPDATED", "COST_CENTER", id, costCenter.getCode() + " " + costCenter.getLabel(),
                costCenter.getCompany().getId(), null);
        return CostCenterView.of(costCenter);
    }

    public void deleteCostCenter(UUID id) {
        CostCenter costCenter = costCenters.findById(id).orElseThrow(() -> new ResourceNotFoundException("CostCenter", id));
        long used = jdbc.sql("select count(*) from vehicle where cost_center_id = :id").param("id", id)
                .query(Long.class).single();
        if (used > 0) {
            throw new BusinessRuleException("COST_CENTER_IN_USE",
                    "Cost center " + costCenter.getCode() + " is still charged with " + used + " vehicles");
        }
        costCenters.delete(costCenter);
        audit.record("COST_CENTER_DELETED", "COST_CENTER", id, costCenter.getCode() + " " + costCenter.getLabel(),
                costCenter.getCompany().getId(), null);
    }

    // ---------------------------------------------------------------- helpers

    /** accountsWith() does not resolve roles: the full account does. */
    private String directoryRole(IdentityDirectory.Account account) {
        return directory.account(account.id()).map(IdentityDirectory.Account::role).orElse(null);
    }

    private Company load(UUID id) {
        return companies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }

    private static void apply(Company company, SaveCompany cmd) {
        company.update(new Company.Details(cmd.name().trim(), cmd.legalName().trim(), cmd.siren(), cmd.segment(),
                blank(cmd.address()), blank(cmd.postalCode()), cmd.city().trim(), blank(cmd.contactName()),
                blank(cmd.contactEmail()), blank(cmd.contactPhone())));
    }

    private void checkUnique(SaveCompany cmd, UUID self) {
        companies.findAll().stream().filter(c -> !c.getId().equals(self)).forEach(other -> {
            if (other.getName().equalsIgnoreCase(cmd.name().trim())) {
                throw new ConflictException("COMPANY_NAME_TAKEN", "A company is already named " + other.getName());
            }
            if (other.getSiren().equals(cmd.siren())) {
                throw new ConflictException("SIREN_TAKEN", "SIREN " + cmd.siren() + " is already used by " + other.getName());
            }
        });
    }

    private void checkSiteName(UUID companyId, String name, UUID self) {
        sites.findByCompanyIdOrderByNameAsc(companyId).stream()
                .filter(s -> !s.getId().equals(self) && s.getName().equalsIgnoreCase(name.trim()))
                .findAny()
                .ifPresent(s -> {
                    throw new ConflictException("SITE_NAME_TAKEN", "The company already has a site named " + s.getName());
                });
    }

    private void checkCode(UUID companyId, String code, UUID self) {
        costCenters.findByCompanyIdOrderByCodeAsc(companyId).stream()
                .filter(c -> !c.getId().equals(self) && c.getCode().equalsIgnoreCase(code.trim()))
                .findAny()
                .ifPresent(c -> {
                    throw new ConflictException("COST_CENTER_CODE_TAKEN", "Cost center " + c.getCode() + " already exists");
                });
    }

    private static void checkVersion(Long expected, long actual, String resource, UUID id) {
        if (expected != null && expected != actual) {
            throw new ObjectOptimisticLockingFailureException(resource, id);
        }
    }

    private Map<UUID, long[]> counts(String sql, UUID companyId) {
        return jdbc.sql(sql).param("company", companyId)
                .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), new long[] {rs.getLong(2), rs.getLong(3)}))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
