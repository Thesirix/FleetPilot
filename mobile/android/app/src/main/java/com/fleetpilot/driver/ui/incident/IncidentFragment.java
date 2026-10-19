package com.fleetpilot.driver.ui.incident;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.DriverSpace;
import com.fleetpilot.driver.data.api.ApiModels.Incident;
import com.fleetpilot.driver.data.api.ApiModels.Vehicle;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.data.repository.DriverRepository.Submission;
import com.fleetpilot.driver.databinding.FragmentIncidentBinding;
import com.fleetpilot.driver.databinding.ItemRowBinding;
import com.fleetpilot.driver.ui.Feedback;
import com.fleetpilot.driver.ui.PhotoStrip;
import com.fleetpilot.driver.ui.Rows;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;
import com.fleetpilot.driver.util.Positions;
import com.fleetpilot.driver.util.Resource;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

/** Declare a claim: type, when, where (with the GPS position), what happened, photos. */
public class IncidentFragment extends Fragment {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private FragmentIncidentBinding binding;
    private IncidentViewModel model;
    private PhotoStrip photos;
    private String type = IncidentViewModel.TYPES.get(0);
    private LocalDateTime occurredAt = LocalDateTime.now(PARIS).truncatedTo(ChronoUnit.MINUTES);
    private Double latitude;
    private Double longitude;

    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (granted.containsValue(true)) {
                    locate();
                } else {
                    Feedback.aboveTabs(Snackbar.make(binding.getRoot(), R.string.incident_gps_refused, Snackbar.LENGTH_SHORT),
                        binding.getRoot()).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentIncidentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(IncidentViewModel.class);
        photos = new PhotoStrip(this, binding.photos);
        binding.history.title.setText(R.string.my_incidents);

        List<String> labels = new ArrayList<>();
        IncidentViewModel.TYPES.forEach(t -> labels.add(Labels.label(requireContext(), "incident_type", t)));
        // A list that never filters on the text shown (the plain adapter would only offer the type already chosen)
        binding.type.setSimpleItems(labels.toArray(new String[0]));
        binding.type.setText(labels.get(0), false);
        binding.type.setOnClickListener(v -> binding.type.showDropDown());
        binding.type.setOnItemClickListener((parent, v, position, id) -> type = IncidentViewModel.TYPES.get(position));

        binding.emergency.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))));
        binding.when.setOnClickListener(v -> pickDate());
        binding.whereLayout.setEndIconOnClickListener(v -> {
            if (Positions.allowed(requireContext())) {
                locate();
            } else {
                locationPermission.launch(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
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
        binding.where.addTextChangedListener(check);
        binding.what.addTextChangedListener(check);
        binding.send.setOnClickListener(v -> send());
        showWhen();

        model.space().observe(getViewLifecycleOwner(), this::render);
        model.outbox().observe(getViewLifecycleOwner(), items -> render(model.space().getValue()));
        model.sending().observe(getViewLifecycleOwner(), sending -> validate());
        model.submitted().observe(getViewLifecycleOwner(), event -> {
            Submission submission = event.take();
            if (submission != null && Feedback.show(binding.getRoot(), submission, R.string.incident_declared)) {
                photos.reset();
                binding.where.setText(null);
                binding.what.setText(null);
                binding.thirdParty.setChecked(false);
                binding.injuries.setChecked(false);
                binding.gpsSaved.setVisibility(View.GONE);
                latitude = null;
                longitude = null;
                occurredAt = LocalDateTime.now(PARIS).truncatedTo(ChronoUnit.MINUTES);
                showWhen();
                binding.getRoot().smoothScrollTo(0, 0);
            }
        });
        model.refresh();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(@Nullable Resource<DriverSpace> resource) {
        if (binding == null || resource == null) {
            return;
        }
        Vehicle vehicle = resource.data == null ? null : resource.data.vehicle;
        binding.noVehicle.setVisibility(resource.data != null && vehicle == null ? View.VISIBLE : View.GONE);
        // Hidden only when the server said there is no vehicle: offline on a first launch, the form stays usable
            binding.form.setVisibility(resource.data != null && vehicle == null ? View.GONE : View.VISIBLE);
        if (vehicle != null) {
            binding.vehicleLine.setText(vehicle.registration + " · " + vehicle.make + " " + vehicle.model);
        }
        ViewGroup list = binding.history.list;
        list.removeAllViews();
        int count = 0;
        List<OutboxItem> waiting = model.outbox().getValue();
        if (waiting != null) {
            for (OutboxItem item : waiting) {
                if (OutboxItem.INCIDENT.equals(item.kind)) {
                    ItemRowBinding row = Rows.add(list, item.summary, null);
                    Rows.badge(row, getString(R.string.outbox_pending_badge), Rows.Tone.WARNING);
                    count++;
                }
            }
        }
        if (resource.data != null && resource.data.driver != null && resource.data.driver.incidents != null) {
            for (Incident i : resource.data.driver.incidents) {
                ItemRowBinding row = Rows.add(list, i.reference,
                        Labels.label(requireContext(), "incident_type", i.type) + " · " + Formats.date(i.occurredAt));
                Rows.badge(row, Labels.label(requireContext(), "incident_status", i.status), Rows.incidentStatus(i.status));
                count++;
            }
        }
        if (count == 0) {
            Rows.empty(list, getString(R.string.no_incident));
        }
    }

    private void locate() {
        binding.whereLayout.setEndIconActivated(true);
        Positions.current(requireContext(), location -> {
            if (binding == null) {
                return;
            }
            if (location == null) {
                Feedback.aboveTabs(Snackbar.make(binding.getRoot(), R.string.incident_gps_refused, Snackbar.LENGTH_SHORT),
                        binding.getRoot()).show();
                return;
            }
            latitude = Math.round(location.getLatitude() * 1e6) / 1e6;
            longitude = Math.round(location.getLongitude() * 1e6) / 1e6;
            CharSequence where = binding.where.getText();
            if (where == null || where.toString().isBlank()) {
                binding.where.setText(getString(R.string.incident_gps_location,
                        String.format(Locale.ROOT, "%.5f", latitude), String.format(Locale.ROOT, "%.5f", longitude)));
            }
            binding.gpsSaved.setVisibility(View.VISIBLE);
        });
    }

    private void validate() {
        if (binding == null) {
            return;
        }
        CharSequence where = binding.where.getText();
        CharSequence what = binding.what.getText();
        boolean sending = Boolean.TRUE.equals(model.sending().getValue());
        binding.send.setEnabled(where != null && !where.toString().isBlank() && what != null
                && !what.toString().isBlank() && !sending);
    }

    private void send() {
        model.declare(type, occurredAt.atZone(PARIS).toInstant(), String.valueOf(binding.where.getText()), latitude,
                longitude, String.valueOf(binding.what.getText()), binding.thirdParty.isChecked(),
                binding.injuries.isChecked(), photos.files());
    }

    private void pickDate() {
        long today = LocalDate.now(PARIS).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
        MaterialDatePicker<Long> date = MaterialDatePicker.Builder.datePicker()
                .setSelection(occurredAt.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
                .setCalendarConstraints(new CalendarConstraints.Builder().setEnd(today)
                        .setValidator(DateValidatorPointBackward.before(today + 86_400_000L)).build())
                .build();
        date.addOnPositiveButtonClickListener(selection -> {
            LocalDate day = Instant.ofEpochMilli(selection).atZone(ZoneId.of("UTC")).toLocalDate();
            MaterialTimePicker time = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(occurredAt.getHour())
                    .setMinute(occurredAt.getMinute())
                    .build();
            time.addOnPositiveButtonClickListener(v -> {
                LocalDateTime chosen = day.atTime(time.getHour(), time.getMinute());
                LocalDateTime now = LocalDateTime.now(PARIS);
                occurredAt = chosen.isAfter(now) ? now.truncatedTo(ChronoUnit.MINUTES) : chosen;
                showWhen();
            });
            time.show(getChildFragmentManager(), "time");
        });
        date.show(getChildFragmentManager(), "date");
    }

    private void showWhen() {
        binding.when.setText(Formats.dateTime(occurredAt.atZone(PARIS).toInstant().toString()));
    }
}
