package com.fleetpilot.quote.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A workshop of the network: dealer, independent garage, fast-fit centre, body shop or truck workshop. */
@Entity
@Table(name = "garage")
public class Garage {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GarageNetwork network;

    private String brand;

    @Column(nullable = false, unique = true, columnDefinition = "bpchar(14)")
    private String siret;

    @Column(nullable = false)
    private String address;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "hourly_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "approved_since")
    private LocalDate approvedSince;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Garage() {
    }

    /** Identity and contact details of a workshop. */
    public record Profile(String name, GarageNetwork network, String brand, String siret, String address,
            String postalCode, String city, BigDecimal latitude, BigDecimal longitude, String email, String phone,
            BigDecimal hourlyRate) {

        public Profile {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(siret, "siret");
            Objects.requireNonNull(postalCode, "postalCode");
            Objects.requireNonNull(hourlyRate, "hourlyRate");
        }
    }

    public Garage(UUID id, String code, Profile profile) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        apply(profile);
    }

    public final void apply(Profile profile) {
        if (profile.hourlyRate().signum() <= 0) {
            throw new BusinessRuleException("INVALID_HOURLY_RATE", "The hourly rate must be positive");
        }
        this.region = Region.ofPostalCode(profile.postalCode()).orElseThrow(() -> new BusinessRuleException(
                "REGION_NOT_COVERED", "No negotiated tariff for the postal code " + profile.postalCode()));
        this.name = profile.name().trim();
        this.network = profile.network();
        this.brand = profile.brand();
        this.siret = profile.siret();
        this.address = profile.address();
        this.postalCode = profile.postalCode();
        this.city = profile.city();
        this.latitude = profile.latitude();
        this.longitude = profile.longitude();
        this.email = profile.email();
        this.phone = profile.phone();
        this.hourlyRate = profile.hourlyRate();
    }

    /** Grants or withdraws the approval of the fleet manager (agrément). */
    public void setApproved(boolean newApproved, LocalDate today) {
        if (newApproved && !approved) {
            this.approvedSince = today;
        }
        if (!newApproved) {
            this.approvedSince = null;
        }
        this.approved = newApproved;
    }

    public void rate(BigDecimal newRating) {
        if (newRating != null && (newRating.compareTo(BigDecimal.ONE) < 0 || newRating.compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw new BusinessRuleException("INVALID_RATING", "The rating goes from 1 to 5");
        }
        this.rating = newRating;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public GarageNetwork getNetwork() {
        return network;
    }

    public String getBrand() {
        return brand;
    }

    public String getSiret() {
        return siret;
    }

    public String getAddress() {
        return address;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public Region getRegion() {
        return region;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public boolean isApproved() {
        return approved;
    }

    public LocalDate getApprovedSince() {
        return approvedSince;
    }

    public BigDecimal getRating() {
        return rating;
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
