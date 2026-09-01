package net.bananemdnsa.historystages.data;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidEntryTest {

    @Test
    void aBareIdLocksEverything() {
        FluidEntry entry = new FluidEntry("minecraft:lava");
        assertEquals("minecraft:lava", entry.getId());
        assertNull(entry.getLockActions(), "null is the 'all actions' marker");
        assertFalse(entry.hasLockActions());
    }

    @Test
    void anEmptyActionListMeansTheSameAsNone() {
        FluidEntry entry = new FluidEntry("minecraft:water", List.of(), null, null);
        assertNull(entry.getLockActions());
    }

    @Test
    void anExplicitActionListIsKept() {
        FluidEntry entry = new FluidEntry("create:molten_iron", List.of("use", "place"), null, null);
        assertEquals(List.of("use", "place"), entry.getLockActions());
        assertTrue(entry.hasLockActions());
    }

    @Test
    void emptyOverridesBecomeNullSoTheStageDefaultWins() {
        FluidEntry entry = new FluidEntry("minecraft:lava", null, "", "");
        assertNull(entry.getNameTextOverride());
        assertNull(entry.getTooltipTextOverride());
        assertFalse(entry.hasNameTextOverride());
        assertFalse(entry.hasTooltipTextOverride());
    }

    @Test
    void overridesAreKeptWhenSet() {
        FluidEntry entry = new FluidEntry("minecraft:lava", null, "???", "Not yet");
        assertEquals("???", entry.getNameTextOverride());
        assertEquals("Not yet", entry.getTooltipTextOverride());
        assertTrue(entry.hasNameTextOverride());
        assertTrue(entry.hasTooltipTextOverride());
    }

    @Test
    void copyDoesNotShareTheActionList() {
        FluidEntry original = new FluidEntry("minecraft:lava",
                new ArrayList<>(List.of("use")), null, null);
        FluidEntry copy = original.copy();

        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getLockActions(), copy.getLockActions());
        assertNotSame(original.getLockActions(), copy.getLockActions());
    }
}
