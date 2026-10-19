package com.fleetpilot.driver.ui.fuel;

import java.time.Instant;
import java.util.List;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.DriverSpace;
import com.fleetpilot.driver.data.api.ApiModels.FuelEntry;
import com.fleetpilot.driver.data.api.ApiModels.RecordFuel;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.domain.FuelRules;
import com.fleetpilot.driver.ui.DriverViewModel;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Resource;

/** A fill-up or a charge typed from the receipt, checked like the server does, then sent through the outbox. */
public class FuelViewModel extends DriverViewModel {

    public FuelViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Resource<DriverSpace>> space() {
        return repository.space();
    }

    public LiveData<Resource<List<FuelEntry>>> history() {
        return repository.fuelEntries();
    }

    public LiveData<List<OutboxItem>> outbox() {
        return repository.outbox();
    }

    public void refresh() {
        repository.refreshSpace();
        repository.refreshFuelEntries();
    }

    public void save(String kind, double quantity, double amount, int mileage, Instant filledAt, String station,
            String payment) {
        RecordFuel entry = new RecordFuel();
        entry.kind = kind;
        entry.quantity = quantity;
        entry.amount = amount;
        entry.mileage = mileage;
        entry.filledAt = filledAt.toString();
        entry.station = station == null || station.isBlank() ? null : station.trim();
        entry.payment = payment;
        String unit = getApplication().getString(FuelRules.CHARGE.equals(kind) ? R.string.unit_kwh : R.string.unit_litre);
        String summary = getApplication().getString(R.string.outbox_fuel, Formats.date(entry.filledAt),
                Formats.number(quantity, 2), unit);
        submit(item(OutboxItem.FUEL, summary, entry));
    }
}
