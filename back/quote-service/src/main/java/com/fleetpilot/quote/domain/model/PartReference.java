package com.fleetpilot.quote.domain.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reference price of a part, excluding tax, per vehicle class. */
@Entity
@Table(name = "part_reference")
public class PartReference {

    @Id
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String family;

    @Column(name = "price_car", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceCar;

    @Column(name = "price_van", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceVan;

    @Column(name = "price_truck", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceTruck;

    protected PartReference() {
    }

    public PartReference(String code, String label, String family, BigDecimal priceCar, BigDecimal priceVan,
            BigDecimal priceTruck) {
        this.code = code;
        this.label = label;
        this.family = family;
        this.priceCar = priceCar;
        this.priceVan = priceVan;
        this.priceTruck = priceTruck;
    }

    public BigDecimal priceFor(TariffClass tariffClass) {
        return switch (tariffClass) {
            case CAR -> priceCar;
            case VAN -> priceVan;
            case TRUCK -> priceTruck;
        };
    }

    /** New reference prices, after a price agreement with the parts distributors. */
    public void reprice(BigDecimal newPriceCar, BigDecimal newPriceVan, BigDecimal newPriceTruck) {
        this.priceCar = newPriceCar;
        this.priceVan = newPriceVan;
        this.priceTruck = newPriceTruck;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getFamily() {
        return family;
    }

    public BigDecimal getPriceCar() {
        return priceCar;
    }

    public BigDecimal getPriceVan() {
        return priceVan;
    }

    public BigDecimal getPriceTruck() {
        return priceTruck;
    }
}
