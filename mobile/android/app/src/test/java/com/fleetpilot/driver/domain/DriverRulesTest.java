package com.fleetpilot.driver.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class DriverRulesTest {

    @Test
    public void an_electric_vehicle_charges_a_plug_in_hybrid_does_both() {
        assertEquals(List.of("CHARGE"), FuelRules.kindsFor("ELECTRIC"));
        assertEquals(List.of("FUEL", "CHARGE"), FuelRules.kindsFor("PLUG_IN_HYBRID"));
        assertEquals(List.of("FUEL"), FuelRules.kindsFor("DIESEL"));
        assertEquals(List.of("FUEL"), FuelRules.kindsFor("HYBRID"));
    }

    @Test
    public void the_unit_price_is_checked_like_the_server_does() {
        assertEquals(1.86, FuelRules.unitPrice(41.5, 77.19), 0.001);
        assertNull(FuelRules.unitPrice(null, 77.0));
        assertNull(FuelRules.unitPrice(0.0, 77.0));
        assertTrue(FuelRules.plausible("FUEL", 1.86));
        // 4 litres for 80 euros: a typo in the quantity
        assertFalse(FuelRules.plausible("FUEL", 20.0));
        assertFalse(FuelRules.plausible("FUEL", 0.30));
        assertTrue(FuelRules.plausible("CHARGE", 0.0));
        assertFalse(FuelRules.plausible("CHARGE", 2.0));
    }

    @Test
    public void every_point_is_ok_until_the_driver_says_otherwise() {
        Checklist checklist = new Checklist();
        assertTrue(checklist.defects().isEmpty());
        assertTrue(checklist.complete());
        assertEquals(8, checklist.answers().size());
    }

    @Test
    public void a_damaged_point_needs_a_comment() {
        Checklist checklist = new Checklist();
        checklist.set("TYRES", Checklist.DAMAGED);
        assertEquals(List.of("TYRES"), checklist.missingComments());
        assertFalse(checklist.complete());

        checklist.comment("TYRES", "Pneu avant droit usé");
        assertTrue(checklist.complete());
        assertEquals(List.of("TYRES"), checklist.defects());
    }

    @Test
    public void back_to_ok_forgets_the_comment() {
        Checklist checklist = new Checklist();
        checklist.set("SAFETY_KIT", Checklist.MISSING);
        checklist.comment("SAFETY_KIT", "Triangle absent");
        checklist.set("SAFETY_KIT", Checklist.OK);
        assertEquals("", checklist.get("SAFETY_KIT").comment());
        assertTrue(checklist.defects().isEmpty());
    }
}
