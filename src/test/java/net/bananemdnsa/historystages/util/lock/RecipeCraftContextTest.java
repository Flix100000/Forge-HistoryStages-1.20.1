package net.bananemdnsa.historystages.util.lock;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A leaked crafter would gate the next, entirely unrelated resolution — one that may well belong
 * to a hopper. Every test here is about the window closing again.
 */
class RecipeCraftContextTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    @Test
    void thereIsNoCrafterUntilAStationSetsOne() {
        assertNull(RecipeCraftContext.crafter());
    }

    @Test
    void theCrafterIsVisibleForTheDurationOfTheResolution() {
        UUID seen = RecipeCraftContext.with(ALICE, RecipeCraftContext::crafter);

        assertEquals(ALICE, seen);
    }

    @Test
    void theWindowClosesAgainAfterwards() {
        RecipeCraftContext.with(ALICE, () -> "resolved");

        assertNull(RecipeCraftContext.crafter());
    }

    @Test
    void aThrownResolutionStillClosesTheWindow() {
        assertThrows(IllegalStateException.class, () ->
                RecipeCraftContext.with(ALICE, () -> {
                    throw new IllegalStateException("recipe blew up");
                }));

        assertNull(RecipeCraftContext.crafter());
    }

    @Test
    void aNestedResolutionGivesTheOuterCrafterBack() {
        UUID inner = RecipeCraftContext.with(ALICE,
                () -> RecipeCraftContext.with(BOB, RecipeCraftContext::crafter));

        assertEquals(BOB, inner);
        assertNull(RecipeCraftContext.crafter());
    }

    @Test
    void aNestedResolutionDoesNotSwallowTheOuterOne() {
        UUID outerAfterInner = RecipeCraftContext.with(ALICE, () -> {
            RecipeCraftContext.with(BOB, () -> "inner");
            return RecipeCraftContext.crafter();
        });

        assertEquals(ALICE, outerAfterInner);
    }

    @Test
    void aStationWithoutAPlayerLeavesItUnset() {
        UUID seen = RecipeCraftContext.with(null, RecipeCraftContext::crafter);

        assertNull(seen);
    }
}
