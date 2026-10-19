package com.fleetpilot.driver.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.databinding.ItemRowBinding;

/**
 * Builds the lists of the cards (deadlines, claims, fill-ups...) with one row layout. The lists are short (at most 50
 * lines, inside a scrolling page): plain views are simpler than a RecyclerView here.
 */
public final class Rows {

    /** Colors of a badge: text and background. */
    public enum Tone {
        NEUTRAL(R.color.text_secondary, R.color.neutral_soft),
        SUCCESS(R.color.success, R.color.success_soft),
        WARNING(R.color.warning, R.color.warning_soft),
        DANGER(R.color.danger, R.color.danger_soft),
        INFO(R.color.info, R.color.info_soft);

        public final int text;
        public final int background;

        Tone(@ColorRes int text, @ColorRes int background) {
            this.text = text;
            this.background = background;
        }
    }

    private Rows() {
    }

    public static ItemRowBinding add(ViewGroup list, String title, @Nullable String detail) {
        if (list.getChildCount() > 0) {
            LayoutInflater.from(list.getContext()).inflate(R.layout.view_divider, list, true);
        }
        ItemRowBinding row = ItemRowBinding.inflate(LayoutInflater.from(list.getContext()), list, true);
        row.title.setText(title);
        if (detail == null || detail.isEmpty()) {
            row.detail.setVisibility(View.GONE);
        } else {
            row.detail.setText(detail);
        }
        return row;
    }

    public static void value(ItemRowBinding row, String value) {
        row.value.setText(value);
        row.value.setVisibility(View.VISIBLE);
    }

    public static void badge(ItemRowBinding row, String text, Tone tone) {
        row.badge.setText(text);
        row.badge.setTextColor(ContextCompat.getColor(row.getRoot().getContext(), tone.text));
        row.badge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(row.getRoot().getContext(), tone.background)));
        row.badge.setVisibility(View.VISIBLE);
    }

    public static void level(ItemRowBinding row, Tone tone) {
        row.level.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(row.getRoot().getContext(), tone.text)));
        row.level.setVisibility(View.VISIBLE);
    }

    public static void empty(ViewGroup list, String text) {
        list.removeAllViews();
        android.widget.TextView empty = (android.widget.TextView) LayoutInflater.from(list.getContext())
                .inflate(R.layout.view_empty, list, false);
        empty.setText(text);
        list.addView(empty);
    }

    public static Tone urgency(String urgency) {
        if ("OVERDUE".equals(urgency)) {
            return Tone.DANGER;
        }
        return "DUE_SOON".equals(urgency) ? Tone.WARNING : Tone.NEUTRAL;
    }

    public static Tone notificationLevel(String level) {
        if ("CRITICAL".equals(level)) {
            return Tone.DANGER;
        }
        return "WARNING".equals(level) ? Tone.WARNING : Tone.INFO;
    }

    public static Tone incidentStatus(String status) {
        return switch (status) {
            case "CLOSED" -> Tone.SUCCESS;
            case "REJECTED" -> Tone.DANGER;
            case "IN_REPAIR", "UNDER_REVIEW" -> Tone.WARNING;
            default -> Tone.INFO;
        };
    }
}
