package com.fleetpilot.driver.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatsTest {

    @Test
    public void numbers_and_amounts_are_written_the_french_way() {
        assertEquals("16 277 km", Formats.km(16277));
        assertEquals("72,50 €", Formats.money(72.5));
        assertEquals("1,807", Formats.number(1.8066, 3));
    }

    @Test
    public void dates_are_shown_in_paris_time() {
        assertEquals("15 oct. 2026", Formats.date("2026-10-15"));
        // 23:30 UTC is already the next day in Paris
        assertEquals("16 oct. 2026", Formats.date("2026-10-15T23:30:00Z"));
        assertEquals("15 oct. 2026, 10:00", Formats.dateTime("2026-10-15T08:00:00Z"));
        assertEquals("-", Formats.date(null));
    }

    @Test
    public void a_large_photo_is_decoded_at_a_reduced_size() {
        assertEquals(1, Photos.sampleSize(1600));
        assertEquals(2, Photos.sampleSize(4000));
        assertEquals(4, Photos.sampleSize(8000));
    }
}
