package com.fleetpilot.driver.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.databinding.ViewPhotosBinding;
import com.fleetpilot.driver.util.Photos;
import com.google.android.material.snackbar.Snackbar;

/**
 * The photos of a claim or an inspection: taken with the camera app or picked in the gallery (the system photo picker,
 * no storage permission needed), resized at once, shown as thumbnails that can be removed.
 */
public final class PhotoStrip {

    public static final int MAX = 10;

    private final Fragment fragment;
    private final ViewPhotosBinding binding;
    private final List<File> files = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Uri> camera;
    private final ActivityResultLauncher<PickVisualMediaRequest> gallery;
    private File cameraFile;

    /** To be created in onCreate or onViewCreated of the fragment (result launchers are registered here). */
    public PhotoStrip(Fragment fragment, ViewPhotosBinding binding) {
        this.fragment = fragment;
        this.binding = binding;
        camera = fragment.registerForActivityResult(new ActivityResultContracts.TakePicture(), taken -> {
            if (Boolean.TRUE.equals(taken) && cameraFile != null) {
                add(Uri.fromFile(cameraFile), cameraFile);
            }
        });
        gallery = fragment.registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX), uris -> {
            for (Uri uri : uris) {
                add(uri, null);
            }
        });
        binding.take.setOnClickListener(v -> {
            Context context = fragment.requireContext();
            cameraFile = Photos.newCameraFile(context);
            camera.launch(FileProvider.getUriForFile(context, context.getPackageName() + ".photos", cameraFile));
        });
        binding.pick.setOnClickListener(v -> gallery.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        render();
    }

    public List<File> files() {
        return List.copyOf(files);
    }

    /** After a submission: the files now belong to the outbox, the strip starts empty. */
    public void reset() {
        files.clear();
        render();
    }

    private void add(Uri uri, File temporary) {
        if (files.size() >= MAX) {
            return;
        }
        Context context = fragment.requireContext().getApplicationContext();
        io.execute(() -> {
            try {
                File ready = Photos.prepare(context, uri);
                main.post(() -> {
                    files.add(ready);
                    render();
                });
            } catch (IOException | RuntimeException e) {
                main.post(() -> Feedback.aboveTabs(Snackbar.make(binding.getRoot(), R.string.photos_failed,
                        Snackbar.LENGTH_SHORT), binding.getRoot()).show());
            } finally {
                if (temporary != null && !temporary.delete()) {
                    temporary.deleteOnExit();
                }
            }
        });
    }

    private void render() {
        if (!fragment.isAdded()) {
            return;
        }
        Context context = fragment.requireContext();
        binding.thumbs.removeAllViews();
        int size = dp(context, 76);
        for (File file : files) {
            FrameLayout frame = new FrameLayout(context);
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription(context.getString(R.string.photos_title));
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            image.setImageBitmap(BitmapFactory.decodeFile(file.getPath(), options));
            image.setClipToOutline(true);
            image.setBackgroundResource(R.drawable.bg_badge);
            frame.addView(image, new FrameLayout.LayoutParams(size, size));
            ImageButton remove = new ImageButton(context);
            remove.setImageResource(R.drawable.ic_close);
            remove.setBackgroundResource(R.drawable.bg_badge);
            remove.setContentDescription(context.getString(R.string.photos_remove));
            int button = dp(context, 26);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(button, button, Gravity.TOP | Gravity.END);
            frame.addView(remove, params);
            remove.setOnClickListener(v -> {
                files.remove(file);
                if (!file.delete()) {
                    file.deleteOnExit();
                }
                render();
            });
            android.widget.LinearLayout.LayoutParams frameParams = new android.widget.LinearLayout.LayoutParams(size, size);
            frameParams.setMarginEnd(dp(context, 8));
            binding.thumbs.addView(frame, frameParams);
        }
        binding.count.setText(context.getString(R.string.photos_count, files.size(), MAX));
        binding.take.setEnabled(files.size() < MAX);
        binding.pick.setEnabled(files.size() < MAX);
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}
