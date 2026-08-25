package net.bananemdnsa.historystages.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollCompletionTest {

    @Test
    void everyModeRoundTripsThroughItsSerializedName() {
        for (ScrollCompletion mode : ScrollCompletion.values()) {
            assertEquals(mode, ScrollCompletion.parse(mode.serialize()));
        }
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertEquals(ScrollCompletion.REPLACE, ScrollCompletion.parse("REPLACE"));
        assertEquals(ScrollCompletion.OPEN, ScrollCompletion.parse("Open"));
    }

    @Test
    void unknownAndMissingValuesFallBackToConsume() {
        // consume is what the pedestal did before this option existed, so an unreadable
        // value must not silently change an existing world's behaviour.
        assertEquals(ScrollCompletion.CONSUME, ScrollCompletion.parse("recycle"));
        assertEquals(ScrollCompletion.CONSUME, ScrollCompletion.parse(null));
    }

    @Test
    void onlyAbsentOrRealValuesCountAsKnown() {
        assertTrue(ScrollCompletion.isKnown(null));
        assertTrue(ScrollCompletion.isKnown("consume"));
        assertFalse(ScrollCompletion.isKnown("recycle"));
    }

    @Test
    void aStageOverrideWinsOverTheConfigDefault() {
        assertEquals(ScrollCompletion.OPEN, ScrollCompletion.resolve("open", "replace"));
    }

    @Test
    void noOverrideFollowsTheConfigDefault() {
        assertEquals(ScrollCompletion.REPLACE, ScrollCompletion.resolve(null, "replace"));
        assertEquals(ScrollCompletion.REPLACE, ScrollCompletion.resolve("", "replace"));
    }

    @Test
    void anUnreadableOverrideFallsBackToTheConfigDefault() {
        // Not to CONSUME: a typo in one stage should behave like "no override given",
        // which is the least surprising reading of a broken file.
        assertEquals(ScrollCompletion.REPLACE, ScrollCompletion.resolve("recycle", "replace"));
    }
}
