package com.fleetpilot.vehicle.application;

import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.vehicle.application.view.Views.CompanyView;
import com.fleetpilot.vehicle.application.view.Views.CostCenterView;
import com.fleetpilot.vehicle.application.view.Views.SiteView;
import com.fleetpilot.vehicle.domain.repository.CompanyRepository;
import com.fleetpilot.vehicle.domain.repository.CostCenterRepository;
import com.fleetpilot.vehicle.domain.repository.SiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Client companies and their sites and cost centers, used to fill the forms and filters. */
@Service
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final CostCenterRepository costCenters;
    private final TenantScope tenant;

    public ReferenceDataService(CompanyRepository companies, SiteRepository sites, CostCenterRepository costCenters,
            TenantScope tenant) {
        this.companies = companies;
        this.sites = sites;
        this.costCenters = costCenters;
        this.tenant = tenant;
    }

    public List<CompanyView> companies() {
        return companies.findAllByOrderByNameAsc().stream()
                .filter(c -> tenant.restrictedCompanyId().map(c.getId()::equals).orElse(true))
                .map(CompanyView::of)
                .toList();
    }

    public CompanyView company(UUID id) {
        tenant.checkAccess(id, "Company", id);
        return companies.findById(id).map(CompanyView::of).orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }

    public List<SiteView> sites(UUID companyId) {
        company(companyId);
        return sites.findByCompanyIdOrderByNameAsc(companyId).stream().map(SiteView::of).toList();
    }

    public List<CostCenterView> costCenters(UUID companyId) {
        company(companyId);
        return costCenters.findByCompanyIdOrderByCodeAsc(companyId).stream().map(CostCenterView::of).toList();
    }
}
