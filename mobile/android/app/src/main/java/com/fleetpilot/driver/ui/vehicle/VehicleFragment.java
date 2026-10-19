package com.fleetpilot.driver.ui.vehicle;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.Contract;
import com.fleetpilot.driver.data.api.ApiModels.Deadline;
import com.fleetpilot.driver.data.api.ApiModels.Vehicle;
import com.fleetpilot.driver.data.repository.DriverRepository;
import com.fleetpilot.driver.databinding.FragmentPageBinding;
import com.fleetpilot.driver.databinding.ItemRowBinding;
import com.fleetpilot.driver.databinding.ViewListCardBinding;
import com.fleetpilot.driver.ui.Cards;
import com.fleetpilot.driver.ui.Rows;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;

/** The file of the vehicle: identity, lease contract and what it includes, deadlines. */
public class VehicleFragment extends Fragment {

    private FragmentPageBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        DriverRepository repository = ((FleetPilotApp) requireActivity().getApplication()).repository();
        binding.refresh.setColorSchemeResources(R.color.primary);
        binding.refresh.setOnRefreshListener(repository::refreshSpace);
        repository.space().observe(getViewLifecycleOwner(), resource -> {
            binding.refresh.setRefreshing(false);
            Vehicle v = resource.data == null ? null : resource.data.vehicle;
            if (v == null) {
                binding.title.setText(R.string.vehicle_title);
                binding.subtitle.setText(R.string.no_vehicle);
                return;
            }
            render(v);
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(Vehicle v) {
        binding.title.setText(v.registration);
        binding.subtitle.setText(v.make + " " + v.model + (v.trimLevel == null ? "" : " " + v.trimLevel));
        binding.content.removeAllViews();

        ViewListCardBinding identity = Cards.add(binding.content, getString(R.string.vehicle_title));
        line(identity, R.string.mileage, Formats.km(v.mileage) + " · " + Formats.date(v.mileageReadAt));
        line(identity, R.string.energy, Labels.label(requireContext(), "energy", v.energy));
        line(identity, R.string.vehicle_critair, v.critAir == 0 ? getString(R.string.critair_electric)
                : String.valueOf(v.critAir));
        line(identity, R.string.vehicle_co2, getString(R.string.vehicle_co2_value, v.co2GramsPerKm));
        line(identity, R.string.vehicle_first_registration, Formats.date(v.firstRegistrationDate));
        line(identity, R.string.vehicle_vin, v.vin);
        line(identity, R.string.site, v.site == null ? null : v.site.name);
        line(identity, R.string.vehicle_company, v.company == null ? null : v.company.name);

        ViewListCardBinding contract = Cards.add(binding.content, getString(R.string.vehicle_contract));
        Contract c = v.activeContract;
        if (c == null) {
            Rows.empty(contract.list, getString("OWNED".equals(v.acquisition) ? R.string.vehicle_owned
                    : R.string.vehicle_no_contract));
        } else {
            line(contract, R.string.vehicle_lessor, c.lessor);
            line(contract, R.string.vehicle_contract_number, c.contractNumber);
            line(contract, R.string.vehicle_contract_end, Formats.date(c.endDate));
            line(contract, R.string.vehicle_contract_mileage, Formats.km(c.contractualMileage));
            StringBuilder services = new StringBuilder();
            append(services, c.maintenanceIncluded, R.string.vehicle_maintenance);
            append(services, c.tyresIncluded, R.string.vehicle_tyres);
            append(services, c.replacementVehicle, R.string.vehicle_replacement);
            line(contract, R.string.vehicle_services, services.length() == 0 ? null : services.toString());
        }

        ViewListCardBinding deadlines = Cards.add(binding.content, getString(R.string.next_deadlines));
        if (v.openDeadlines == null || v.openDeadlines.isEmpty()) {
            Rows.empty(deadlines.list, getString(R.string.no_deadline));
        } else {
            for (Deadline d : v.openDeadlines) {
                String due = d.dueDate != null ? Formats.date(d.dueDate)
                        : Formats.km(d.dueMileage == null ? 0 : d.dueMileage);
                ItemRowBinding row = Rows.add(deadlines.list, Labels.label(requireContext(), "deadline", d.type), due);
                Rows.badge(row, Labels.label(requireContext(), "urgency", d.urgency), Rows.urgency(d.urgency));
            }
        }
    }

    private void append(StringBuilder services, boolean included, int label) {
        if (included) {
            if (services.length() > 0) {
                services.append(", ");
            }
            services.append(getString(label));
        }
    }

    private void line(ViewListCardBinding card, int label, @Nullable String value) {
        ItemRowBinding row = Rows.add(card.list, getString(label), null);
        Rows.value(row, value == null ? "-" : value);
        row.title.setTextAppearance(R.style.Text_Secondary);
    }
}
