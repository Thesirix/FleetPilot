package com.fleetpilot.driver.ui.garage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.net.Uri;
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
import com.fleetpilot.driver.data.api.ApiModels.NearbyGarage;
import com.fleetpilot.driver.databinding.FragmentGaragesBinding;
import com.fleetpilot.driver.databinding.ItemGarageBinding;
import com.fleetpilot.driver.ui.Rows;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;
import com.fleetpilot.driver.util.Positions;
import com.fleetpilot.driver.util.Resource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

/** The approved garages around the phone, nearest first, on a map and in a list with call and directions. */
public class GaragesFragment extends Fragment {

    private static final int CLOSEST_FRAMED = 8;

    private FragmentGaragesBinding binding;
    private Location origin;

    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (granted.containsValue(true)) {
                    locate();
                } else if (binding != null) {
                    binding.status.setText(R.string.garages_permission);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentGaragesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.map.setTileSource(TileSourceFactory.MAPNIK);
        binding.map.setMultiTouchControls(true);
        binding.map.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        binding.locate.setOnClickListener(v -> start());
        binding.radius.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked && origin != null) {
                search();
            }
        });
        start();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.map.onResume();
    }

    @Override
    public void onPause() {
        binding.map.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        binding.map.onDetach();
        binding = null;
        super.onDestroyView();
    }

    private void start() {
        if (Positions.allowed(requireContext())) {
            locate();
        } else {
            permission.launch(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void locate() {
        binding.status.setText(R.string.garages_locating);
        Positions.current(requireContext(), location -> {
            if (binding == null) {
                return;
            }
            if (location == null) {
                binding.status.setText(R.string.garages_permission);
                return;
            }
            origin = location;
            search();
        });
    }

    private int radius() {
        int id = binding.radius.getCheckedButtonId();
        return id == R.id.radius_10 ? 10 : id == R.id.radius_50 ? 50 : 25;
    }

    private void search() {
        int radius = radius();
        binding.status.setText(R.string.loading);
        View root = binding.getRoot();
        ((FleetPilotApp) requireActivity().getApplication()).repository().nearbyGarages(origin.getLatitude(),
                origin.getLongitude(), radius, result -> root.post(() -> render(result, radius)));
    }

    private void render(Resource<List<NearbyGarage>> result, int radius) {
        if (binding == null) {
            return;
        }
        binding.list.removeAllViews();
        if (result.data == null) {
            binding.status.setText(Labels.error(requireContext(), result.error));
            binding.mapCard.setVisibility(View.GONE);
            return;
        }
        List<NearbyGarage> garages = result.data;
        binding.status.setText(garages.isEmpty() ? getString(R.string.garages_none)
                : getResources().getQuantityString(R.plurals.garages_found, garages.size(), garages.size(), radius));
        drawMap(garages);
        int index = 1;
        for (NearbyGarage g : garages) {
            ItemGarageBinding card = ItemGarageBinding.inflate(getLayoutInflater(), binding.list, true);
            card.index.setText(String.valueOf(index++));
            card.name.setText(g.name);
            card.network.setText(Labels.label(requireContext(), "network", g.network)
                    + (g.brand == null ? "" : " · " + g.brand));
            card.distance.setText(Formats.number(g.distanceKm, 1) + " km");
            card.address.setText((g.address == null ? "" : g.address + ", ") + (g.postalCode == null ? "" : g.postalCode + " ")
                    + g.city);
            List<String> facts = new ArrayList<>();
            if (g.rating != null) {
                facts.add("★ " + Formats.number(g.rating, 1));
            }
            if (g.hourlyRate != null) {
                facts.add(getString(R.string.garages_rate, Formats.money(g.hourlyRate)));
            }
            card.facts.setText(String.join(" · ", facts));
            card.facts.setVisibility(facts.isEmpty() ? View.GONE : View.VISIBLE);
            if (g.phone == null) {
                card.call.setVisibility(View.GONE);
            } else {
                card.call.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:" + g.phone.replace(" ", "")))));
            }
            card.go.setOnClickListener(v -> directions(g));
        }
        if (garages.isEmpty()) {
            Rows.empty(binding.list, getString(R.string.garages_none));
        }
    }

    private void drawMap(List<NearbyGarage> garages) {
        binding.mapCard.setVisibility(View.VISIBLE);
        binding.map.getOverlays().clear();
        GeoPoint me = new GeoPoint(origin.getLatitude(), origin.getLongitude());
        Marker position = new Marker(binding.map);
        position.setPosition(me);
        position.setIcon(pin(null, R.color.info));
        position.setTitle(getString(R.string.garages_around_me));
        position.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        binding.map.getOverlays().add(position);
        List<GeoPoint> points = new ArrayList<>();
        points.add(me);
        int index = 1;
        for (NearbyGarage g : garages) {
            if (g.latitude == null || g.longitude == null) {
                continue;
            }
            GeoPoint point = new GeoPoint(g.latitude, g.longitude);
            Marker marker = new Marker(binding.map);
            marker.setPosition(point);
            marker.setIcon(pin(String.valueOf(index), R.color.primary));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setTitle(index++ + ". " + g.name);
            marker.setSnippet(Formats.number(g.distanceKm, 1) + " km");
            binding.map.getOverlays().add(marker);
            // The view frames the position and the closest garages (the list is sorted by distance): framing all of
            // them piles the pins up on a phone screen; the others appear when zooming out
            if (index <= CLOSEST_FRAMED + 1) {
                points.add(point);
            }
        }
        binding.map.post(() -> {
            if (binding == null) {
                return;
            }
            if (points.size() == 1) {
                binding.map.getController().setZoom(13.0);
                binding.map.getController().setCenter(me);
            } else {
                binding.map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.2f), false, 24);
            }
            binding.map.invalidate();
        });
    }

    /** A round pin with the number of the garage in the list, or a plain dot for the position of the phone. */
    private BitmapDrawable pin(@Nullable String label, int color) {
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round((label == null ? 18 : 26) * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(requireContext().getColor(color));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2.5f * density, paint);
        if (label != null) {
            paint.setColor(Color.WHITE);
            paint.setTextSize(11 * density);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint);
        }
        return new BitmapDrawable(getResources(), bitmap);
    }

    /** The maps app of the phone, or the browser when there is none. */
    private void directions(NearbyGarage g) {
        String coords = String.format(Locale.ROOT, "%f,%f", g.latitude, g.longitude);
        Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + coords + "?q=" + coords + "(" + Uri.encode(g.name) + ")"));
        if (maps.resolveActivity(requireContext().getPackageManager()) == null) {
            maps = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/directions?to=" + coords.replace(",", "%2C")));
        }
        startActivity(maps);
    }
}
