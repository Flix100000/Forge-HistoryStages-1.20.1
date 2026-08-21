package net.bananemdnsa.historystages.data.scroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollSortTest {

    @Test
    void everyOrderRoundTripsThroughItsSerializedName() {
        for (OpenScrollSort sort : OpenScrollSort.values()) {
            assertEquals(sort, OpenScrollSort.parse(sort.serialize()));
        }
    }

    @Test
    void anUnknownOrderFallsBackToDefinedRatherThanReshufflingThePack() {
        assertEquals(OpenScrollSort.DEFINED, OpenScrollSort.parse("random"));
        assertEquals(OpenScrollSort.DEFINED, OpenScrollSort.parse(null));
        assertEquals(OpenScrollSort.DEFINED, OpenScrollSort.parse("  "));
    }

    @Test
    void parsingIgnoresCaseAndSurroundingSpace() {
        assertEquals(OpenScrollSort.ALPHABETICAL, OpenScrollSort.parse("  ALPHABETICAL "));
    }

    @Test
    void onlyAlphabeticalReordersAnything() {
        assertTrue(OpenScrollSort.ALPHABETICAL.sortsByName());
        assertFalse(OpenScrollSort.DEFINED.sortsByName());
    }
}
