package com.fleetpilot.vehicle.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** An employee of a client company who drives company vehicles. */
@Entity
@Table(name = "driver")
public class Driver {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @Column(name = "employee_number", nullable = false)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(name = "license_number", nullable = false)
    private String licenseNumber;

    @Convert(converter = LicenseCategoriesConverter.class)
    @Column(name = "license_categories", nullable = false)
    private Set<LicenseCategory> licenseCategories = EnumSet.noneOf(LicenseCategory.class);

    @Column(name = "license_issued_on", nullable = false)
    private LocalDate licenseIssuedOn;

    @Column(name = "license_expires_on")
    private LocalDate licenseExpiresOn;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Driver() {
    }

    /** Identity and contact details of a driver. */
    public record Profile(String employeeNumber, String firstName, String lastName, String email, String phone) {
    }

    /** Driving licence of a driver. */
    public record License(String number, Set<LicenseCategory> categories, LocalDate issuedOn, LocalDate expiresOn) {

        public License {
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(issuedOn, "issuedOn");
            categories = categories == null || categories.isEmpty()
                    ? EnumSet.noneOf(LicenseCategory.class) : EnumSet.copyOf(categories);
            if (expiresOn != null && expiresOn.isBefore(issuedOn)) {
                throw new BusinessRuleException("LICENSE_DATES_INVALID", "A licence cannot expire before it is issued");
            }
        }
    }

    public Driver(UUID id, Company company, Profile profile, License license) {
        this.id = Objects.requireNonNull(id);
        this.company = Objects.requireNonNull(company);
        setProfile(profile);
        setLicense(license);
    }

    public void updateProfile(Profile profile) {
        setProfile(profile);
    }

    public void updateLicense(License license) {
        setLicense(license);
    }

    /** Not overridable: called from the constructor (and entity methods must not be final for Hibernate proxies). */
    private void setProfile(Profile profile) {
        this.employeeNumber = profile.employeeNumber();
        this.firstName = profile.firstName();
        this.lastName = profile.lastName();
        this.email = profile.email();
        this.phone = profile.phone();
    }

    private void setLicense(License license) {
        this.licenseNumber = license.number();
        this.licenseCategories = EnumSet.noneOf(LicenseCategory.class);
        this.licenseCategories.addAll(license.categories());
        this.licenseIssuedOn = license.issuedOn();
        this.licenseExpiresOn = license.expiresOn();
    }

    public void moveTo(Site newSite) {
        if (newSite != null && !newSite.getCompany().getId().equals(getCompanyId())) {
            throw new BusinessRuleException("SITE_OF_ANOTHER_COMPANY", "The site belongs to another company");
        }
        this.site = newSite;
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    /** True when the licence is valid on the given day and covers the vehicle category. */
    public boolean canDrive(VehicleCategory category, LocalDate on) {
        if (licenseExpiresOn != null && licenseExpiresOn.isBefore(on)) {
            return false;
        }
        return category.requiredLicense()
                .map(required -> required.coveredBy().stream().anyMatch(licenseCategories::contains))
                .orElse(true);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public UUID getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public UUID getCompanyId() {
        return company.getId();
    }

    public Site getSite() {
        return site;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public Set<LicenseCategory> getLicenseCategories() {
        return EnumSet.copyOf(licenseCategories.isEmpty() ? EnumSet.noneOf(LicenseCategory.class) : licenseCategories);
    }

    public LocalDate getLicenseIssuedOn() {
        return licenseIssuedOn;
    }

    public LocalDate getLicenseExpiresOn() {
        return licenseExpiresOn;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
