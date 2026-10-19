package com.fleetpilot.driver.ui.profile;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.fleetpilot.driver.BuildConfig;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.DriverDetail;
import com.fleetpilot.driver.data.api.ApiModels.DriverSummary;
import com.fleetpilot.driver.data.repository.DriverRepository;
import com.fleetpilot.driver.databinding.FragmentPageBinding;
import com.fleetpilot.driver.databinding.ItemRowBinding;
import com.fleetpilot.driver.databinding.ViewListCardBinding;
import com.fleetpilot.driver.ui.Cards;
import com.fleetpilot.driver.ui.MainActivity;
import com.fleetpilot.driver.ui.Rows;
import com.fleetpilot.driver.util.Formats;
import com.google.android.material.button.MaterialButton;

/** The driver: identity, driving licence, kilometres driven, and sign-out. */
public class ProfileFragment extends Fragment {

    private FragmentPageBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FleetPilotApp app = (FleetPilotApp) requireActivity().getApplication();
        DriverRepository repository = app.repository();
        binding.title.setText(app.auth().fullName());
        binding.subtitle.setText(R.string.profile_title);
        binding.refresh.setColorSchemeResources(R.color.primary);
        binding.refresh.setOnRefreshListener(repository::refreshSpace);

        ViewListCardBinding identity = Cards.add(binding.content, getString(R.string.profile_title));
        ViewListCardBinding license = Cards.add(binding.content, getString(R.string.profile_license));
        MaterialButton logout = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        logout.setText(R.string.profile_logout);
        logout.setIconResource(R.drawable.ic_logout);
        logout.setTextColor(requireContext().getColor(R.color.danger));
        logout.setIconTint(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.danger)));
        logout.setOnClickListener(v -> ((MainActivity) requireActivity()).logout());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                getResources().getDisplayMetrics());
        binding.content.addView(logout, params);
        android.widget.TextView version = new android.widget.TextView(requireContext());
        version.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME));
        version.setTextAppearance(R.style.Text_Muted);
        version.setGravity(android.view.Gravity.CENTER);
        version.setPadding(0, 16, 0, 0);
        binding.content.addView(version);

        repository.space().observe(getViewLifecycleOwner(), resource -> {
            binding.refresh.setRefreshing(false);
            if (resource.data == null || resource.data.driver == null) {
                return;
            }
            DriverDetail detail = resource.data.driver;
            DriverSummary d = detail.driver;
            identity.list.removeAllViews();
            line(identity, R.string.profile_employee, d.employeeNumber);
            line(identity, R.string.profile_email, d.email);
            line(identity, R.string.profile_phone, d.phone);
            line(identity, R.string.site, d.site == null ? null : d.site.name + " (" + d.site.city + ")");
            line(identity, R.string.profile_driven, Formats.km(detail.drivenKilometres));
            license.list.removeAllViews();
            line(license, R.string.profile_license_categories,
                    d.licenseCategories == null ? null : String.join(", ", d.licenseCategories));
            line(license, R.string.profile_license_expires, Formats.date(d.licenseExpiresOn));
        });
        repository.refreshSpace();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void line(ViewListCardBinding card, int label, @Nullable String value) {
        ItemRowBinding row = Rows.add(card.list, getString(label), null);
        Rows.value(row, value == null ? "-" : value);
        row.title.setTextAppearance(R.style.Text_Secondary);
    }
}
