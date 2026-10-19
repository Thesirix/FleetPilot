package com.fleetpilot.driver.notifications;

import java.util.List;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;
import com.fleetpilot.driver.ui.MainActivity;
import com.fleetpilot.driver.util.Labels;

/** Shows the notifications of the server in the status bar of the phone; a tap opens the list in the app. */
public final class Notifier {

    public static final String CHANNEL_URGENT = "urgent";
    public static final String CHANNEL_INFO = "info";

    private Notifier() {
    }

    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel urgent = new NotificationChannel(CHANNEL_URGENT,
                context.getString(R.string.channel_urgent), NotificationManager.IMPORTANCE_HIGH);
        urgent.setDescription(context.getString(R.string.channel_urgent_description));
        NotificationChannel info = new NotificationChannel(CHANNEL_INFO,
                context.getString(R.string.channel_info), NotificationManager.IMPORTANCE_DEFAULT);
        info.setDescription(context.getString(R.string.channel_info_description));
        manager.createNotificationChannel(urgent);
        manager.createNotificationChannel(info);
    }

    public static boolean allowed(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /** Shows each one; the most serious go to the urgent channel (sound, heads-up). */
    public static void show(Context context, List<DriverNotification> fresh) {
        if (!allowed(context)) {
            return;
        }
        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_SCREEN, MainActivity.SCREEN_NOTIFICATIONS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        for (DriverNotification n : fresh) {
            boolean critical = "CRITICAL".equals(n.level);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                    critical ? CHANNEL_URGENT : CHANNEL_INFO)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.primary))
                    .setContentTitle(Labels.notificationTitle(context, n))
                    .setContentText(Labels.notificationText(context, n))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(Labels.notificationText(context, n)))
                    .setPriority(critical ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(tap)
                    .setAutoCancel(true);
            try {
                manager.notify(n.id.hashCode(), builder.build());
            } catch (SecurityException e) {
                return;
            }
        }
    }
}
