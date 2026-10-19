package com.fleetpilot.driver.util;

import android.annotation.SuppressLint;
import android.content.Context;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;

/**
 * French labels of the codes sent by the API (energy, deadline type, alert type...), read from the string resources
 * named {@code <group>_<CODE>}: label(context, "energy", "PLUG_IN_HYBRID") is "Hybride rechargeable".
 */
public final class Labels {

    private Labels() {
    }

    @SuppressLint("DiscouragedApi")
    public static String label(Context context, String group, String code) {
        if (code == null) {
            return "-";
        }
        int id = context.getResources().getIdentifier(group + "_" + code, "string", context.getPackageName());
        return id == 0 ? code : context.getString(id);
    }

    /**
     * French message of an error code of the API. A code without its own message never shows the English detail of the
     * server: it falls back to a message by kind (server down, refused).
     */
    public static String error(Context context, String code) {
        String label = label(context, "error", code);
        if (code == null || !label.equals(code)) {
            return code == null ? context.getString(R.string.error_generic) : label;
        }
        return code.startsWith("HTTP_5") ? context.getString(R.string.error_server_unavailable)
                : context.getString(R.string.error_refused);
    }

    public static String notificationTitle(Context context, DriverNotification n) {
        return switch (n.kind) {
            case "DEADLINE" -> label(context, "deadline", n.code);
            case "VEHICLE_ALERT" -> label(context, "alert", n.code);
            default -> context.getString(R.string.notification_incident, n.detail == null ? "" : n.detail);
        };
    }

    public static String notificationText(Context context, DriverNotification n) {
        return switch (n.kind) {
            case "DEADLINE" -> {
                String due = n.dueDate != null ? Formats.date(n.dueDate) : Formats.km(n.dueMileage == null ? 0 : n.dueMileage);
                yield "OVERDUE".equals(n.detail)
                        ? context.getString(R.string.notification_overdue, due)
                        : context.getString(R.string.notification_due_soon, due);
            }
            case "VEHICLE_ALERT" -> n.detail == null ? "" : n.detail;
            default -> context.getString(R.string.notification_incident_status, label(context, "incident_status", n.code));
        };
    }
}
