package com.fleetpilot.driver.ui.notifications;

import java.util.List;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;
import com.fleetpilot.driver.data.repository.DriverRepository;
import com.fleetpilot.driver.databinding.FragmentPageBinding;
import com.fleetpilot.driver.databinding.ItemRowBinding;
import com.fleetpilot.driver.databinding.ViewListCardBinding;
import com.fleetpilot.driver.notifications.Notifier;
import com.fleetpilot.driver.ui.Cards;
import com.fleetpilot.driver.ui.Rows;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;
import com.fleetpilot.driver.util.Resource;

/** What the driver should know; opening the list marks everything as read. */
public class NotificationsFragment extends Fragment {

    private FragmentPageBinding binding;
    private ViewListCardBinding card;
    private DriverRepository repository;

    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (binding != null) {
                    render(repository.notifications().getValue());
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = ((FleetPilotApp) requireActivity().getApplication()).repository();
        binding.title.setText(R.string.notifications_title);
        binding.subtitle.setText(R.string.notifications_subtitle);
        card = Cards.add(binding.content, getString(R.string.notifications_title));
        binding.refresh.setColorSchemeResources(R.color.primary);
        binding.refresh.setOnRefreshListener(repository::refreshNotifications);
        repository.notifications().observe(getViewLifecycleOwner(), this::render);
        repository.refreshNotifications();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(@Nullable Resource<List<DriverNotification>> resource) {
        if (binding == null || resource == null) {
            return;
        }
        binding.refresh.setRefreshing(false);
        ViewGroup list = card.list;
        list.removeAllViews();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.allowed(requireContext())) {
            card.link.setText(R.string.notifications_enable);
            card.link.setVisibility(View.VISIBLE);
            card.link.setOnClickListener(v -> permission.launch(Manifest.permission.POST_NOTIFICATIONS));
        } else {
            card.link.setVisibility(View.GONE);
        }
        if (resource.data == null) {
            return;
        }
        if (resource.data.isEmpty()) {
            Rows.empty(list, getString(R.string.notifications_none));
            return;
        }
        for (DriverNotification n : resource.data) {
            ItemRowBinding row = Rows.add(list, Labels.notificationTitle(requireContext(), n),
                    Labels.notificationText(requireContext(), n)
                            + ("DEADLINE".equals(n.kind) ? "" : "\n" + Formats.dateTime(n.at)));
            Rows.level(row, Rows.notificationLevel(n.level));
            Rows.badge(row, Labels.label(requireContext(), "level", n.level), Rows.notificationLevel(n.level));
        }
        repository.markNotificationsSeen(resource.data);
    }
}
