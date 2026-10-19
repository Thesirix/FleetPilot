package com.fleetpilot.driver.ui.fuel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.FuelEntry;
import com.fleetpilot.driver.data.api.ApiModels.Vehicle;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.data.repository.DriverRepository.Submission;
import com.fleetpilot.driver.databinding.FragmentFuelBinding;
import com.fleetpilot.driver.databinding.ItemRowBinding;
import com.fleetpilot.driver.domain.FuelRules;
import com.fleetpilot.driver.ui.Feedback;
import com.fleetpilot.driver.ui.Rows;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;
import com.fleetpilot.driver.util.Resource;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

/** Enter a fill-up or a charge; the history of the driver below. */
public class FuelFragment extends Fragment {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private FragmentFuelBinding binding;
    private FuelViewModel model;
    private String kind = FuelRules.FUEL;
    private LocalDateTime filledAt = LocalDateTime.now(PARIS).truncatedTo(ChronoUnit.MINUTES);
    private boolean mileageFilled;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentFuelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(FuelViewModel.class);
        binding.history.title.setText(R.string.fuel_history);
        TextWatcher check = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validate();
            }
        };
        binding.quantity.addTextChangedListener(check);
        binding.amount.addTextChangedListener(check);
        binding.odometer.addTextChangedListener(check);
        binding.kind.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                setKind(id == R.id.kind_charge ? FuelRules.CHARGE : FuelRules.FUEL);
            }
        });
        binding.payment.addOnButtonCheckedListener((group, id, checked) ->
                binding.personalHint.setVisibility(id == R.id.payment_personal && checked ? View.VISIBLE : View.GONE));
        binding.when.setOnClickListener(v -> pickDate());
        binding.save.setOnClickListener(v -> save());
        showWhen();
        setKind(kind);

        model.space().observe(getViewLifecycleOwner(), resource -> {
            Vehicle vehicle = resource.data == null ? null : resource.data.vehicle;
            binding.noVehicle.setVisibility(resource.data != null && vehicle == null ? View.VISIBLE : View.GONE);
            // Hidden only when the server said there is no vehicle: offline on a first launch, the form stays usable
            binding.form.setVisibility(resource.data != null && vehicle == null ? View.GONE : View.VISIBLE);
            if (vehicle != null) {
                bindVehicle(vehicle);
            }
        });
        model.history().observe(getViewLifecycleOwner(), resource -> renderHistory(resource, model.outbox().getValue()));
        model.outbox().observe(getViewLifecycleOwner(), items -> renderHistory(model.history().getValue(), items));
        model.sending().observe(getViewLifecycleOwner(), sending -> validate());
        model.submitted().observe(getViewLifecycleOwner(), event -> {
            Submission submission = event.take();
            if (submission != null && Feedback.show(binding.getRoot(), submission, R.string.fuel_saved)) {
                binding.quantity.setText(null);
                binding.amount.setText(null);
                binding.station.setText(null);
                filledAt = LocalDateTime.now(PARIS).truncatedTo(ChronoUnit.MINUTES);
                showWhen();
            }
        });
        model.refresh();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void bindVehicle(Vehicle vehicle) {
        List<String> kinds = FuelRules.kindsFor(vehicle.energy);
        binding.kind.setVisibility(kinds.size() > 1 ? View.VISIBLE : View.GONE);
        binding.vehicleLine.setVisibility(kinds.size() > 1 ? View.GONE : View.VISIBLE);
        if (!kinds.contains(kind)) {
            setKind(kinds.get(0));
        }
        if (kinds.size() > 1) {
            binding.kind.check(FuelRules.CHARGE.equals(kind) ? R.id.kind_charge : R.id.kind_fuel);
        }
        binding.vehicleLine.setText(vehicle.registration + " · " + Labels.label(requireContext(), "fuel_kind", kind));
        if (!mileageFilled) {
            binding.odometer.setText(String.valueOf(vehicle.mileage));
            mileageFilled = true;
        }
    }

    private void setKind(String value) {
        kind = value;
        String unit = getString(FuelRules.CHARGE.equals(kind) ? R.string.unit_kwh : R.string.unit_litre);
        binding.quantityLayout.setHint(getString(R.string.fuel_quantity, unit));
        binding.vehicleLine.setCompoundDrawablesRelativeWithIntrinsicBounds(
                FuelRules.CHARGE.equals(kind) ? R.drawable.ic_bolt : R.drawable.ic_fuel, 0, 0, 0);
        validate();
    }

    @Nullable
    private static Double number(CharSequence text) {
        try {
            return text == null || text.length() == 0 ? null : Double.parseDouble(text.toString().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void validate() {
        if (binding == null) {
            return;
        }
        Double quantity = number(binding.quantity.getText());
        Double amount = number(binding.amount.getText());
        Double mileage = number(binding.odometer.getText());
        Double price = FuelRules.unitPrice(quantity, amount);
        String unit = getString(FuelRules.CHARGE.equals(kind) ? R.string.unit_kwh : R.string.unit_litre);
        boolean plausible = price == null || FuelRules.plausible(kind, price);
        if (price == null) {
            binding.unitPrice.setVisibility(View.GONE);
        } else {
            binding.unitPrice.setVisibility(View.VISIBLE);
            binding.unitPrice.setText(getString(plausible ? R.string.fuel_unit_price : R.string.fuel_implausible,
                    Formats.number(price, 3), unit));
            binding.unitPrice.setTextColor(requireContext().getColor(plausible ? R.color.text_secondary : R.color.danger));
        }
        boolean sending = Boolean.TRUE.equals(model.sending().getValue());
        binding.save.setEnabled(price != null && plausible && mileage != null && mileage >= 0 && !sending);
    }

    private void save() {
        Double quantity = number(binding.quantity.getText());
        Double amount = number(binding.amount.getText());
        Double mileage = number(binding.odometer.getText());
        if (quantity == null || amount == null || mileage == null) {
            return;
        }
        String payment = binding.payment.getCheckedButtonId() == R.id.payment_personal ? "PERSONAL" : "FUEL_CARD";
        Instant when = filledAt.atZone(PARIS).toInstant();
        model.save(kind, quantity, amount, mileage.intValue(), when, String.valueOf(binding.station.getText()), payment);
    }

    private void pickDate() {
        long today = LocalDate.now(PARIS).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
        MaterialDatePicker<Long> date = MaterialDatePicker.Builder.datePicker()
                .setSelection(filledAt.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
                .setCalendarConstraints(new CalendarConstraints.Builder().setEnd(today)
                        .setValidator(DateValidatorPointBackward.before(today + 86_400_000L)).build())
                .build();
        date.addOnPositiveButtonClickListener(selection -> {
            LocalDate day = Instant.ofEpochMilli(selection).atZone(ZoneId.of("UTC")).toLocalDate();
            MaterialTimePicker time = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(filledAt.getHour())
                    .setMinute(filledAt.getMinute())
                    .build();
            time.addOnPositiveButtonClickListener(v -> {
                LocalDateTime chosen = day.atTime(time.getHour(), time.getMinute());
                LocalDateTime now = LocalDateTime.now(PARIS);
                filledAt = chosen.isAfter(now) ? now.truncatedTo(ChronoUnit.MINUTES) : chosen;
                showWhen();
            });
            time.show(getChildFragmentManager(), "time");
        });
        date.show(getChildFragmentManager(), "date");
    }

    private void showWhen() {
        binding.when.setText(Formats.dateTime(filledAt.atZone(PARIS).toInstant().toString()));
    }

    private void renderHistory(@Nullable Resource<List<FuelEntry>> resource, @Nullable List<OutboxItem> waiting) {
        if (binding == null) {
            return;
        }
        ViewGroup list = binding.history.list;
        list.removeAllViews();
        int count = 0;
        if (waiting != null) {
            for (OutboxItem item : waiting) {
                if (OutboxItem.FUEL.equals(item.kind)) {
                    ItemRowBinding row = Rows.add(list, item.summary, null);
                    Rows.badge(row, getString(R.string.outbox_pending_badge), Rows.Tone.WARNING);
                    count++;
                }
            }
        }
        if (resource != null && resource.data != null) {
            for (FuelEntry e : resource.data) {
                String unit = getString(FuelRules.CHARGE.equals(e.kind) ? R.string.unit_kwh : R.string.unit_litre);
                ItemRowBinding row = Rows.add(list, Formats.number(e.quantity, 2) + " " + unit + " · "
                        + Formats.dateTime(e.filledAt), (e.station == null ? getString(R.string.fuel_no_station) : e.station)
                        + " · " + Formats.km(e.mileage));
                Rows.value(row, Formats.money(e.amount));
                Rows.badge(row, Labels.label(requireContext(), "payment", e.payment),
                        "PERSONAL".equals(e.payment) ? Rows.Tone.WARNING : Rows.Tone.NEUTRAL);
                count++;
            }
        }
        if (count == 0) {
            Rows.empty(list, getString(R.string.fuel_none));
        }
    }
}
