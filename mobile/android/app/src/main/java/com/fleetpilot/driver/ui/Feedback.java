package com.fleetpilot.driver.ui;

import android.view.View;
import androidx.annotation.StringRes;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.repository.DriverRepository.Outcome;
import com.fleetpilot.driver.data.repository.DriverRepository.Submission;
import com.fleetpilot.driver.util.Labels;
import com.google.android.material.snackbar.Snackbar;

/** Tells the driver what became of what they submitted: sent, kept on the phone, or refused with the reason. */
public final class Feedback {

    private Feedback() {
    }

    /** True unless refused: the form can be cleared. */
    public static boolean show(View anchor, Submission submission, @StringRes int sentMessage) {
        if (submission.outcome() == Outcome.SENT) {
            aboveTabs(Snackbar.make(anchor, sentMessage, Snackbar.LENGTH_SHORT), anchor).show();
            return true;
        }
        if (submission.outcome() == Outcome.QUEUED) {
            aboveTabs(Snackbar.make(anchor, R.string.outbox_queued, Snackbar.LENGTH_LONG), anchor).show();
            return true;
        }
        String text = Labels.error(anchor.getContext(), submission.code());
        aboveTabs(Snackbar.make(anchor, text, Snackbar.LENGTH_LONG), anchor)
                .setBackgroundTint(anchor.getContext().getColor(R.color.danger))
                .setTextColor(anchor.getContext().getColor(R.color.surface))
                .show();
        return false;
    }

    /** The message stays above the tab bar: over it, it would swallow the taps on the tabs while it is shown. */
    public static Snackbar aboveTabs(Snackbar snackbar, View anchor) {
        View tabs = anchor.getRootView().findViewById(R.id.tabs);
        if (tabs != null && tabs.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(tabs);
        }
        return snackbar;
    }
}
