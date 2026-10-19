package com.fleetpilot.driver.ui.home;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.Contract;
import com.fleetpilot.driver.data.api.ApiModels.Deadline;
import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;
import com.fleetpilot.driver.data.api.ApiModels.DriverSpace;
import com.fleetpilot.driver.data.api.ApiModels.Incident;
import com.fleetpilot.driver.data.api.ApiModels.MileagePoint;
import com.fleetpilot.driver.data.api.ApiModels.Vehicle;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.data.repository.DriverRepository.Submission;
import com.fleetpilot.driver.databinding.DialogMileageBinding;
import com.fleetpilot.driver.databinding.FragmentHomeBinding;
import com.fleetpilot.driver.databinding.ItemActionBinding;
import com.fleetpilot.driver.databinding.ItemRowBinding;
import com.fleetpilot.driver.ui.Feedback;
import com.fleetpilot.driver.ui.MainActivity;
import com.fleetpilot.driver.ui.Rows;
import com.fleetpilot.driver.ui.notifications.NotificationsFragment;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;
import com.fleetpilot.driver.util.Resource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Home: my vehicle and its mileage, shortcuts, notifications, deadlines and claims, and what waits to be sent. */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel model;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(HomeViewModel.class);
        MainActivity activity = (MainActivity) requireActivity();
        String firstName = ((FleetPilotApp) activity.getApplication()).auth().firstName();
        binding.hello.setText(getString(R.string.hello, firstName));

        action(binding.actionFuel, R.drawable.ic_fuel, R.string.quick_fuel, R.string.quick_fuel_hint,
                R.color.tone_amber, R.color.tone_amber_soft, v -> activity.selectTab(R.id.tab_fuel));
        action(binding.actionInspection, R.drawable.ic_inspection, R.string.quick_inspection,
                R.string.quick_inspection_hint, R.color.tone_teal, R.color.tone_teal_soft,
                v -> activity.selectTab(R.id.tab_inspection));
        action(binding.actionIncident, R.drawable.ic_incident, R.string.quick_incident, R.string.quick_incident_hint,
                R.color.tone_red, R.color.tone_red_soft, v -> activity.selectTab(R.id.tab_incident));
        action(binding.actionGarages, R.drawable.ic_place, R.string.quick_garages, R.string.quick_garages_hint,
                R.color.tone_blue, R.color.tone_blue_soft, v -> activity.selectTab(R.id.tab_garages));
        binding.nextCard.setOnClickListener(v -> binding.scroll.smoothScrollTo(0, binding.deadlines.getRoot().getTop()));

        binding.notifications.title.setText(R.string.notifications_preview);
        binding.notifications.link.setText(R.string.see_all);
        binding.notifications.link.setVisibility(View.VISIBLE);
        binding.notifications.link.setOnClickListener(v -> activity.open(new NotificationsFragment()));
        binding.deadlines.title.setText(R.string.next_deadlines);
        binding.incidents.title.setText(R.string.my_incidents);
        binding.incidents.link.setText(R.string.quick_incident);
        binding.incidents.link.setVisibility(View.VISIBLE);
        binding.incidents.link.setOnClickListener(v -> activity.selectTab(R.id.tab_incident));

        binding.reportMileage.setOnClickListener(v -> askMileage());
        binding.openVehicle.setOnClickListener(v -> activity.openVehicle());
        binding.outboxRetry.setOnClickListener(v -> model.retryOutbox());
        binding.refresh.setColorSchemeResources(R.color.primary);
        binding.refresh.setOnRefreshListener(model::refresh);

        model.space().observe(getViewLifecycleOwner(), this::render);
        model.outbox().observe(getViewLifecycleOwner(), this::renderOutbox);
        model.notifications().observe(getViewLifecycleOwner(), this::renderNotifications);
        model.submitted().observe(getViewLifecycleOwner(), event -> {
            Submission submission = event.take();
            if (submission != null) {
                Feedback.show(binding.getRoot(), submission, R.string.mileage_recorded);
            }
        });
        model.refresh();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /** A shortcut, in the colour of its action (fill-up amber, inspection teal, claim red, garages blue). */
    private void action(ItemActionBinding action, int icon, int label, int hint, @ColorRes int tone,
            @ColorRes int soft, View.OnClickListener click) {
        action.icon.setImageResource(icon);
        action.icon.setImageTintList(ColorStateList.valueOf(requireContext().getColor(tone)));
        action.icon.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(soft)));
        action.wash.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(soft)));
        action.label.setText(label);
        action.hint.setText(hint);
        action.getRoot().setOnClickListener(click);
    }

    private void render(Resource<DriverSpace> resource) {
        binding.refresh.setRefreshing(resource.loading && resource.data != null);
        binding.stale.setVisibility(resource.stale ? View.VISIBLE : View.GONE);
        DriverSpace space = resource.data;
        if (space == null) {
            return;
        }
        Vehicle v = space.vehicle;
        binding.noVehicle.setVisibility(v == null ? View.VISIBLE : View.GONE);
        binding.vehicle.setVisibility(v == null ? View.GONE : View.VISIBLE);
        binding.actions.setVisibility(v == null ? View.GONE : View.VISIBLE);
        binding.deadlines.getRoot().setVisibility(v == null ? View.GONE : View.VISIBLE);
        if (v != null) {
            binding.plate.setText(v.registration);
            binding.model.setText(getString(R.string.vehicle_model_line, v.make, v.model));
            binding.mileage.setText(Formats.km(v.mileage));
            binding.energy.setText(Labels.label(requireContext(), "energy", v.energy));
            binding.site.setText(v.site == null ? "-" : v.site.name);
            critAir(v.critAir);
            renderUsage(v);
            renderNext(v.openDeadlines);
            renderDeadlines(v.openDeadlines);
        } else {
            binding.nextCard.setVisibility(View.GONE);
        }
        renderIncidents(space.driver == null ? null : space.driver.incidents);
    }

    private void critAir(int value) {
        int[] colors = {R.color.critair_e, R.color.critair_1, R.color.critair_2, R.color.critair_3, R.color.critair_4,
            R.color.critair_5};
        int index = Math.max(0, Math.min(5, value));
        binding.critair.setText(getString(R.string.critair_label, value == 0 ? "E" : String.valueOf(value)));
        // Dark text on the yellow sticker 2, white on the others (as in the web app)
        binding.critair.setTextColor(index == 2 ? 0xFF422006 : 0xFFFFFFFF);
        binding.critair.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(colors[index])));
        binding.critair.setContentDescription(binding.critair.getText());
    }

    /**
     * Under the figures of the vehicle: the lease contract (kilometres used against the mileage allowed) or, for a
     * vehicle owned by the company, the kilometres of the last 30 days.
     */
    private void renderUsage(Vehicle v) {
        Contract c = v.activeContract;
        boolean contract = c != null && c.contractualMileage > 0;
        binding.contract.setVisibility(contract ? View.VISIBLE : View.GONE);
        if (contract) {
            int used = Math.max(0, v.mileage - c.startMileage);
            binding.contractLabel.setText(getString(R.string.contract_of, c.lessor));
            binding.contractUse.setText(getString(R.string.contract_use, Formats.km(used), Formats.km(c.contractualMileage)));
            binding.contractBar.setProgressCompat(Math.min(100, used * 100 / c.contractualMileage), true);
        }
        Integer month = monthKilometres(v.mileageHistory);
        binding.monthKm.setVisibility(!contract && month != null ? View.VISIBLE : View.GONE);
        if (!contract && month != null) {
            binding.monthKm.setText(getString(R.string.month_km, Formats.km(month)));
        }
    }

    @Nullable
    private static Integer monthKilometres(@Nullable List<MileagePoint> readings) {
        if (readings == null) {
            return null;
        }
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int count = 0;
        for (MileagePoint r : readings) {
            if (r.readAt != null && Instant.parse(r.readAt).isAfter(since)) {
                min = Math.min(min, r.mileage);
                max = Math.max(max, r.mileage);
                count++;
            }
        }
        return count > 1 ? max - min : null;
    }

    /** The deadline that comes first, by date or by mileage, put forward under the vehicle. */
    private void renderNext(@Nullable List<Deadline> deadlines) {
        Deadline next = null;
        for (Deadline d : deadlines == null ? List.<Deadline>of() : deadlines) {
            if (next == null || closeness(d) < closeness(next)) {
                next = d;
            }
        }
        binding.nextCard.setVisibility(next == null ? View.GONE : View.VISIBLE);
        if (next == null) {
            return;
        }
        Rows.Tone tone = Rows.urgency(next.urgency);
        int color = requireContext().getColor(tone == Rows.Tone.NEUTRAL ? R.color.info : tone.text);
        int soft = requireContext().getColor(tone == Rows.Tone.NEUTRAL ? R.color.info_soft : tone.background);
        binding.nextBar.setBackgroundColor(color);
        binding.nextIcon.setImageTintList(ColorStateList.valueOf(color));
        binding.nextIcon.setBackgroundTintList(ColorStateList.valueOf(soft));
        binding.nextType.setText(Labels.label(requireContext(), "deadline", next.type));
        binding.nextDue.setText(remaining(next));
        binding.nextBadge.setText(Labels.label(requireContext(), "urgency", next.urgency));
        binding.nextBadge.setTextColor(requireContext().getColor(tone.text));
        binding.nextBadge.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(tone.background)));
    }

    /** Days before the deadline; a mileage counts as one day per 50 km, about what a company car drives. */
    private static double closeness(Deadline d) {
        if (d.daysRemaining != null) {
            return d.daysRemaining;
        }
        return d.kilometresRemaining == null ? Double.MAX_VALUE : d.kilometresRemaining / 50.0;
    }

    private String remaining(Deadline d) {
        if (d.daysRemaining != null) {
            long days = d.daysRemaining;
            if (days == 0) {
                return getString(R.string.deadline_today);
            }
            return days < 0
                    ? getResources().getQuantityString(R.plurals.deadline_late_days, (int) -days, (int) -days)
                    : getResources().getQuantityString(R.plurals.deadline_in_days, (int) days, (int) days);
        }
        int km = d.kilometresRemaining == null ? 0 : d.kilometresRemaining;
        return km < 0 ? getString(R.string.deadline_late_km, Formats.km(-km)) : getString(R.string.deadline_in_km, Formats.km(km));
    }

    private void renderDeadlines(@Nullable List<Deadline> deadlines) {
        ViewGroup list = binding.deadlines.list;
        list.removeAllViews();
        if (deadlines == null || deadlines.isEmpty()) {
            Rows.empty(list, getString(R.string.no_deadline));
            return;
        }
        for (Deadline d : deadlines) {
            String due = d.dueDate != null ? Formats.date(d.dueDate) : Formats.km(d.dueMileage == null ? 0 : d.dueMileage);
            ItemRowBinding row = Rows.add(list, Labels.label(requireContext(), "deadline", d.type), due);
            Rows.badge(row, Labels.label(requireContext(), "urgency", d.urgency), Rows.urgency(d.urgency));
        }
    }

    private void renderIncidents(@Nullable List<Incident> incidents) {
        ViewGroup list = binding.incidents.list;
        list.removeAllViews();
        if (incidents == null || incidents.isEmpty()) {
            Rows.empty(list, getString(R.string.no_incident));
            return;
        }
        for (Incident i : incidents) {
            ItemRowBinding row = Rows.add(list, i.reference,
                    Labels.label(requireContext(), "incident_type", i.type) + " · " + Formats.date(i.occurredAt));
            Rows.badge(row, Labels.label(requireContext(), "incident_status", i.status), Rows.incidentStatus(i.status));
        }
    }

    private void renderNotifications(Resource<List<DriverNotification>> resource) {
        ViewGroup list = binding.notifications.list;
        if (resource.data == null) {
            return;
        }
        list.removeAllViews();
        if (resource.data.isEmpty()) {
            Rows.empty(list, getString(R.string.notifications_none));
            return;
        }
        for (DriverNotification n : resource.data.subList(0, Math.min(3, resource.data.size()))) {
            ItemRowBinding row = Rows.add(list, Labels.notificationTitle(requireContext(), n),
                    Labels.notificationText(requireContext(), n));
            Rows.level(row, Rows.notificationLevel(n.level));
        }
    }

    private void renderOutbox(List<OutboxItem> items) {
        binding.outboxCard.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        ViewGroup list = binding.outboxList;
        list.removeAllViews();
        for (OutboxItem item : items) {
            String detail;
            if (item.isRejected()) {
                detail = Labels.error(requireContext(), item.errorCode);
            } else {
                detail = getString(R.string.outbox_waiting, Formats.dateTime(java.time.Instant.ofEpochMilli(item.createdAt).toString()));
                if (!item.photos().isEmpty()) {
                    detail += " · " + getString(R.string.outbox_photos, item.sentPhotos().size(), item.photos().size());
                }
            }
            ItemRowBinding row = Rows.add(list, item.summary, detail);
            if (item.isRejected()) {
                row.detail.setTextColor(requireContext().getColor(R.color.danger));
            }
            row.action.setImageResource(R.drawable.ic_delete);
            row.action.setContentDescription(getString(R.string.outbox_discard));
            row.action.setVisibility(View.VISIBLE);
            row.action.setOnClickListener(v -> model.discard(item.id));
        }
    }

    private void askMileage() {
        Resource<DriverSpace> current = model.space().getValue();
        int mileage = current != null && current.data != null && current.data.vehicle != null
                ? current.data.vehicle.mileage : 0;
        DialogMileageBinding dialog = DialogMileageBinding.inflate(getLayoutInflater());
        EditText input = dialog.mileageInput;
        input.setText(String.valueOf(mileage));
        input.setSelection(input.getText().length());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.report_mileage)
                .setView(dialog.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        model.reportMileage(Integer.parseInt(text));
                    }
                })
                .show();
        input.requestFocus();
    }
}
