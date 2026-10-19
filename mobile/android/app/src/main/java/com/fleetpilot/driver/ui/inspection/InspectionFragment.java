package com.fleetpilot.driver.ui.inspection;

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
import com.fleetpilot.driver.data.api.ApiModels.CheckAnswer;
import com.fleetpilot.driver.data.api.ApiModels.Inspection;
import com.fleetpilot.driver.data.api.ApiModels.Vehicle;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.data.repository.DriverRepository.Submission;
import com.fleetpilot.driver.databinding.FragmentInspectionBinding;
import com.fleetpilot.driver.databinding.ItemCheckBinding;
import com.fleetpilot.driver.databinding.ItemRowBinding;
import com.fleetpilot.driver.domain.Checklist;
import com.fleetpilot.driver.ui.Feedback;
import com.fleetpilot.driver.ui.PhotoStrip;
import com.fleetpilot.driver.ui.Rows;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;
import com.fleetpilot.driver.util.Resource;

/** Inspection of the vehicle: the checklist, the fuel or battery level, photos; the last inspections below. */
public class InspectionFragment extends Fragment {

    private FragmentInspectionBinding binding;
    private InspectionViewModel model;
    private PhotoStrip photos;
    private boolean electric;
    private boolean mileageFilled;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentInspectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(InspectionViewModel.class);
        photos = new PhotoStrip(this, binding.photos);
        binding.history.title.setText(R.string.inspection_history);
        binding.level.addOnChangeListener((slider, value, fromUser) -> showLevel());
        binding.save.setOnClickListener(v -> save());
        binding.odometer.addTextChangedListener(new TextWatcher() {
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
        });
        buildChecklist();
        showLevel();

        model.space().observe(getViewLifecycleOwner(), resource -> {
            Vehicle vehicle = resource.data == null ? null : resource.data.vehicle;
            binding.noVehicle.setVisibility(resource.data != null && vehicle == null ? View.VISIBLE : View.GONE);
            // Hidden only when the server said there is no vehicle: offline on a first launch, the form stays usable
            binding.form.setVisibility(resource.data != null && vehicle == null ? View.GONE : View.VISIBLE);
            if (vehicle != null) {
                electric = "ELECTRIC".equals(vehicle.energy);
                showLevel();
                if (!mileageFilled) {
                    binding.odometer.setText(String.valueOf(vehicle.mileage));
                    mileageFilled = true;
                }
            }
        });
        model.history().observe(getViewLifecycleOwner(), resource -> renderHistory(resource, model.outbox().getValue()));
        model.outbox().observe(getViewLifecycleOwner(), items -> renderHistory(model.history().getValue(), items));
        model.sending().observe(getViewLifecycleOwner(), sending -> validate());
        model.submitted().observe(getViewLifecycleOwner(), event -> {
            Submission submission = event.take();
            if (submission != null && Feedback.show(binding.getRoot(), submission, R.string.inspection_saved)) {
                photos.reset();
                binding.remarks.setText(null);
                buildChecklist();
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

    private void buildChecklist() {
        Checklist checklist = model.checklist();
        binding.checklist.removeAllViews();
        for (String item : Checklist.ITEMS) {
            if (binding.checklist.getChildCount() > 0) {
                getLayoutInflater().inflate(R.layout.view_divider, binding.checklist, true);
            }
            ItemCheckBinding row = ItemCheckBinding.inflate(getLayoutInflater(), binding.checklist, true);
            row.getRoot().setTag(item);
            row.label.setText(Labels.label(requireContext(), "item", item));
            Checklist.Answer answer = checklist.get(item);
            row.conditions.check(switch (answer.condition()) {
                case Checklist.DAMAGED -> R.id.damaged;
                case Checklist.MISSING -> R.id.missing;
                default -> R.id.ok;
            });
            row.comment.setText(answer.comment());
            row.commentLayout.setVisibility(Checklist.OK.equals(answer.condition()) ? View.GONE : View.VISIBLE);
            row.conditions.addOnButtonCheckedListener((group, id, checked) -> {
                if (!checked) {
                    return;
                }
                String condition = id == R.id.damaged ? Checklist.DAMAGED : id == R.id.missing ? Checklist.MISSING
                        : Checklist.OK;
                checklist.set(item, condition);
                row.commentLayout.setVisibility(Checklist.OK.equals(condition) ? View.GONE : View.VISIBLE);
                if (!Checklist.OK.equals(condition)) {
                    row.comment.requestFocus();
                }
                validate();
            });
            row.comment.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    checklist.comment(item, s.toString());
                    validate();
                }
            });
        }
        validate();
    }

    private void showLevel() {
        int value = Math.round(binding.level.getValue());
        binding.levelLabel.setText(getString(electric ? R.string.inspection_level_battery : R.string.inspection_level_fuel,
                value));
    }

    private void validate() {
        if (binding == null) {
            return;
        }
        Checklist checklist = model.checklist();
        int defects = checklist.defects().size();
        binding.defects.setText(defects == 0 ? getString(R.string.inspection_no_defect)
                : getResources().getQuantityString(R.plurals.inspection_defects, defects, defects));
        binding.defects.setTextColor(requireContext().getColor(defects == 0 ? R.color.text_muted : R.color.warning));
        boolean complete = checklist.complete();
        binding.missingComment.setVisibility(complete ? View.GONE : View.VISIBLE);
        CharSequence mileage = binding.odometer.getText();
        boolean sending = Boolean.TRUE.equals(model.sending().getValue());
        binding.save.setEnabled(complete && mileage != null && mileage.length() > 0 && !sending);
    }

    private void save() {
        CharSequence mileage = binding.odometer.getText();
        if (mileage == null || mileage.length() == 0) {
            return;
        }
        int id = binding.kind.getCheckedButtonId();
        String kind = id == R.id.kind_pickup ? "PICKUP" : id == R.id.kind_return ? "RETURN" : "PERIODIC";
        model.save(kind, Integer.parseInt(mileage.toString()), Math.round(binding.level.getValue()),
                String.valueOf(binding.remarks.getText()), photos.files());
    }

    private void renderHistory(@Nullable Resource<List<Inspection>> resource, @Nullable List<OutboxItem> waiting) {
        if (binding == null) {
            return;
        }
        ViewGroup list = binding.history.list;
        list.removeAllViews();
        int count = 0;
        if (waiting != null) {
            for (OutboxItem item : waiting) {
                if (OutboxItem.INSPECTION.equals(item.kind)) {
                    ItemRowBinding row = Rows.add(list, item.summary, null);
                    Rows.badge(row, getString(R.string.outbox_pending_badge), Rows.Tone.WARNING);
                    count++;
                }
            }
        }
        if (resource != null && resource.data != null) {
            for (Inspection i : resource.data) {
                StringBuilder detail = new StringBuilder(Formats.km(i.mileage) + " · " + i.fuelLevel + " %");
                if (i.checks != null) {
                    for (CheckAnswer c : i.checks) {
                        if (!Checklist.OK.equals(c.condition)) {
                            detail.append("\n").append(Labels.label(requireContext(), "item", c.item)).append(" : ")
                                    .append(c.comment == null ? "" : c.comment);
                        }
                    }
                }
                ItemRowBinding row = Rows.add(list, Labels.label(requireContext(), "inspection_kind", i.kind) + " · "
                        + Formats.dateTime(i.performedAt), detail.toString());
                Rows.badge(row, i.defects == 0 ? getString(R.string.inspection_no_defect)
                                : getResources().getQuantityString(R.plurals.inspection_defects, i.defects, i.defects),
                        i.defects == 0 ? Rows.Tone.SUCCESS : Rows.Tone.WARNING);
                count++;
            }
        }
        if (count == 0) {
            Rows.empty(list, getString(R.string.inspection_none));
        }
    }
}
