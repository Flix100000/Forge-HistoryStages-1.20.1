package net.bananemdnsa.historystages.api.lock;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two action vocabularies against each other.
 *
 * <p>The lists are a compatibility surface, not a label: they are inverted into the
 * {@code unlock_actions} key on disk, so reordering or renaming one silently rewrites every
 * stage file that narrowed itself to an action.
 */
class LockActionsTest {

    /**
     * There were briefly three vocabularies, the third naming the two sides of a merchant offer.
     * It went when the trade category stopped gating items and started naming whole trades:
     * either a player may make a given trade or they may not, and there is no half of one to
     * narrow to. Stated as a test because "why is there no buy/sell any more" is a reasonable
     * question to ask this file.
     */
    @Test
    void thereIsNoSeparateTradeVocabulary() {
        assertFalse(LockActions.KNOWN.contains("buy"));
        assertFalse(LockActions.KNOWN.contains("sell"));
        assertTrue(LockActions.KNOWN.contains("trade"),
                "the broad rule stayed: an item entry can still say nobody trades with this");
    }

    @Test
    void theItemVocabularyIsTheElevenTheModShipsToday() {
        assertEquals(
                List.of("equip", "attack", "place", "break", "pickup", "trade",
                        "use", "loot", "recipe", "gui", "icon"),
                LockActions.ITEM);
    }

    /**
     * Not a cosmetic addition. {@code unlock_actions} stores the <em>complement</em> over this
     * list, so an action that appears in no existing file counts as locked in all of them: an
     * entry someone narrowed to "use only" gates trading too from the update onwards. That is
     * the intended effect and must not be migrated away — see the design doc, §4.2.
     */
    @Test
    void tradingSitsWithTheOtherWaysOfAcquiringSomething() {
        assertTrue(LockActions.ITEM.contains("trade"));
        assertEquals(LockActions.ITEM.indexOf("pickup") + 1, LockActions.ITEM.indexOf("trade"),
                "trade belongs next to pickup - the actions popup groups by neighbour");
    }

    @Test
    void theFluidVocabularyDropsWhatAFluidCannotDo() {
        assertEquals(
                List.of("use", "place", "pickup", "recipe", "ingredient", "loot", "icon"),
                LockActions.FLUID);
    }

    @Test
    void ingredientExistsOnlyForFluids() {
        assertTrue(LockActions.FLUID.contains("ingredient"));
        assertFalse(LockActions.ITEM.contains("ingredient"));
    }

    @Test
    void everyFluidActionThatIsNotIngredientIsAlsoAnItemAction() {
        for (String action : LockActions.FLUID) {
            if (action.equals("ingredient")) continue;
            assertTrue(LockActions.ITEM.contains(action),
                    action + " is offered for fluids but is not a known action");
        }
    }
}
