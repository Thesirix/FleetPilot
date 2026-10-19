package com.fleetpilot.quote.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Negotiated hourly rate, excluding tax, for a region and a labour category. */
@Entity
@Table(name = "labour_rate")
public class LabourRate {

    /** Region and labour category. */
    @Embeddable
    public record Key(
            @Enumerated(EnumType.STRING) @Column(name = "region") Region region,
            @Enumerated(EnumType.STRING) @Column(name = "labour_category") LabourCategory labourCategory)
            implements Serializable {
    }

    @EmbeddedId
    private Key key;

    @Column(name = "hourly_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "truck_hourly_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal truckHourlyRate;

    protected LabourRate() {
    }

    public LabourRate(Region region, LabourCategory category, BigDecimal hourlyRate, BigDecimal truckHourlyRate) {
        this.key = new Key(region, category);
        this.hourlyRate = hourlyRate;
        this.truckHourlyRate = truckHourlyRate;
    }

    public BigDecimal rateFor(TariffClass tariffClass) {
        return tariffClass == TariffClass.TRUCK ? truckHourlyRate : hourlyRate;
    }

    /** New negotiated rates, after a price agreement with the networks of the region. */
    public void update(BigDecimal newHourlyRate, BigDecimal newTruckHourlyRate) {
        if (newHourlyRate.signum() <= 0 || newTruckHourlyRate.signum() <= 0) {
            throw new BusinessRuleException("INVALID_HOURLY_RATE", "Hourly rates must be positive");
        }
        this.hourlyRate = newHourlyRate;
        this.truckHourlyRate = newTruckHourlyRate;
    }

    public Key getKey() {
        return key;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public BigDecimal getTruckHourlyRate() {
        return truckHourlyRate;
    }
}
