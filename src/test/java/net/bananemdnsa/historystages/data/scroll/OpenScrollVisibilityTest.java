package net.bananemdnsa.historystages.data.scroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollVisibilityTest {

    @Test
    void everyValueRoundTripsThroughItsSerializedName() {
        for (OpenScrollVisibility value : OpenScrollVisibility.values()) {
            assertEquals(value, OpenScrollVisibility.parse(value.serialize()));
        }
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertEquals(OpenScrollVisibility.VISIBLE, OpenScrollVisibility.parse("VISIBLE"));
    }

    @Test
    void unknownAndMissingValuesStayObscured() {
        // Falling back to VISIBLE would leak a pack's contents because of a typo.
        assertEquals(OpenScrollVisibility.OBSCURED, OpenScrollVisibility.parse("hidden"));
        assertEquals(OpenScrollVisibility.OBSCURED, OpenScrollVisibility.parse(null));
    }

    @Test
    void hidesLockedEntriesOnlyWhenObscured() {
        assertTrue(OpenScrollVisibility.OBSCURED.hidesLocked());
        assertFalse(OpenScrollVisibility.VISIBLE.hidesLocked());
    }
}
