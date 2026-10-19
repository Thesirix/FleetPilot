package com.fleetpilot.vehicle.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A client company whose fleet is managed by FleetPilot. It is the tenant of every other fleet record. */
@Entity
@Table(name = "company")
public class Company {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(nullable = false, unique = true, length = 9)
    private String siren;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanySegment segment;

    @Column(nullable = false)
    private String city;

    private String address;

    @Column(name = "postal_code", length = 5)
    private String postalCode;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Company() {
    }

    public Company(UUID id, String name, String legalName, String siren, CompanySegment segment, String city) {
        this.id = id;
        this.name = name;
        this.legalName = legalName;
        this.siren = siren;
        this.segment = segment;
        this.city = city;
        this.createdAt = Instant.now();
    }

    /** The details of the company: name, legal identity, segment, address and contact at the client. */
    public record Details(String name, String legalName, String siren, CompanySegment segment, String address,
            String postalCode, String city, String contactName, String contactEmail, String contactPhone) {
    }

    public void update(Details details) {
        this.name = details.name();
        this.legalName = details.legalName();
        this.siren = details.siren();
        this.segment = details.segment();
        this.address = details.address();
        this.postalCode = details.postalCode();
        this.city = details.city();
        this.contactName = details.contactName();
        this.contactEmail = details.contactEmail();
        this.contactPhone = details.contactPhone();
    }

    /** A company that leaves FleetPilot keeps its history; its users can no longer sign in. */
    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getSiren() {
        return siren;
    }

    public CompanySegment getSegment() {
        return segment;
    }

    public String getCity() {
        return city;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getAddress() {
        return address;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
