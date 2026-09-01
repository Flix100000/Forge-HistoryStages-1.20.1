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

    @Test
    void theItemVocabularyIsTheTenTheModHasAlwaysShipped() {
        assertEquals(
                List.of("equip", "attack", "place", "break", "pickup",
                        "use", "loot", "recipe", "gui", "icon"),
                LockActions.ITEM);
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

    @Test
    void theKnownSetIsTheUnionOfBoth() {
        assertTrue(LockActions.KNOWN.containsAll(LockActions.ITEM));
        assertTrue(LockActions.KNOWN.containsAll(LockActions.FLUID));
        assertEquals(11, LockActions.KNOWN.size());
    }
}
