package com.fleetpilot.driver.ui;

import androidx.annotation.Nullable;

/** A value read once: a message shown after a submission must not show again when the screen is rotated. */
public final class Event<T> {

    private final T value;
    private boolean handled;

    public Event(T value) {
        this.value = value;
    }

    @Nullable
    public T take() {
        if (handled) {
            return null;
        }
        handled = true;
        return value;
    }
}
