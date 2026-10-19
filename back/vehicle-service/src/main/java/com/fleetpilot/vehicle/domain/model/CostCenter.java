package com.fleetpilot.vehicle.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Budget line of a client company that fleet costs are charged to. */
@Entity
@Table(name = "cost_center")
public class CostCenter {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Version
    private long version;

    protected CostCenter() {
    }

    public CostCenter(UUID id, Company company, String code, String label) {
        this.id = id;
        this.company = company;
        this.code = code;
        this.label = label;
    }

    public void update(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public long getVersion() {
        return version;
    }

    public UUID getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
