package com.fleetpilot.quote.domain.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Standard labour time of an operation ("temps barème"), in hours, per vehicle class. */
@Entity
@Table(name = "labour_operation")
public class LabourOperation {

    @Id
    private String code;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "labour_category", nullable = false)
    private LabourCategory labourCategory;

    @Column(name = "hours_car", nullable = false, precision = 5, scale = 2)
    private BigDecimal hoursCar;

    @Column(name = "hours_van", nullable = false, precision = 5, scale = 2)
    private BigDecimal hoursVan;

    @Column(name = "hours_truck", nullable = false, precision = 5, scale = 2)
    private BigDecimal hoursTruck;

    protected LabourOperation() {
    }

    public LabourOperation(String code, String label, LabourCategory labourCategory, BigDecimal hoursCar,
            BigDecimal hoursVan, BigDecimal hoursTruck) {
        this.code = code;
        this.label = label;
        this.labourCategory = labourCategory;
        this.hoursCar = hoursCar;
        this.hoursVan = hoursVan;
        this.hoursTruck = hoursTruck;
    }

    public BigDecimal hoursFor(TariffClass tariffClass) {
        return switch (tariffClass) {
            case CAR -> hoursCar;
            case VAN -> hoursVan;
            case TRUCK -> hoursTruck;
        };
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public LabourCategory getLabourCategory() {
        return labourCategory;
    }

    public BigDecimal getHoursCar() {
        return hoursCar;
    }

    public BigDecimal getHoursVan() {
        return hoursVan;
    }

    public BigDecimal getHoursTruck() {
        return hoursTruck;
    }
}
