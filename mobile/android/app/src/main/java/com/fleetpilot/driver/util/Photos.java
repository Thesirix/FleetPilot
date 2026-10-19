package com.fleetpilot.driver.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;

/**
 * Photos taken by the driver: resized to 1600 pixels on the longest side and turned upright (the camera stores the
 * orientation apart), then kept as JPEG files in the private storage of the app until the outbox has sent them.
 */
public final class Photos {

    private static final int MAX_SIDE = 1600;
    private static final int QUALITY = 82;

    private Photos() {
    }

    /** A new empty file for the camera to write into (shared with it through the FileProvider). */
    public static File newCameraFile(Context context) {
        File dir = new File(context.getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create " + dir);
        }
        return new File(dir, UUID.randomUUID() + ".jpg");
    }

    /** Resizes the picture into the outbox folder; the file is named after the id of the photo. */
    public static File prepare(Context context, Uri source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Not an image: " + source);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(Math.max(bounds.outWidth, bounds.outHeight));
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Unreadable image: " + source);
        }
        int rotation;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            rotation = in == null ? 0 : new ExifInterface(in).getRotationDegrees();
        }
        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        Bitmap ready = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        File dir = new File(context.getFilesDir(), "outbox");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        File file = new File(dir, UUID.randomUUID() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            ready.compress(Bitmap.CompressFormat.JPEG, QUALITY, out);
        }
        if (ready != bitmap) {
            ready.recycle();
        }
        bitmap.recycle();
        return file;
    }

    /** Power of two that brings the image close to the target without loading 12 megapixels in memory. */
    static int sampleSize(int longestSide) {
        int sample = 1;
        while (longestSide / (sample * 2) >= MAX_SIDE) {
            sample *= 2;
        }
        return sample;
    }
}
