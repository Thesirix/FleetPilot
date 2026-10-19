package com.fleetpilot.driver.ui;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.fleetpilot.driver.databinding.ViewListCardBinding;

/** Adds a titled card with a list to a page. */
public final class Cards {

    private Cards() {
    }

    public static ViewListCardBinding add(ViewGroup page, String title) {
        ViewListCardBinding card = ViewListCardBinding.inflate(LayoutInflater.from(page.getContext()), page, false);
        card.title.setText(title);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
                page.getResources().getDisplayMetrics());
        page.addView(card.getRoot(), params);
        return card;
    }
}
