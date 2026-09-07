package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.api.lock.LockActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The actions popup is a grid of checkboxes with no error state: a group that should have been
 * dropped shows actions the category cannot honour, nothing crashes, and the maintainer finds out
 * when a lock they switched off keeps locking.
 *
 * <p>So which groups each vocabulary gets is asked here rather than in front of a running client.
 */
class LockActionGroupsTest {

    private static List<String> headings(List<String> vocabulary) {
        List<String> names = new ArrayList<>();
        for (String[] group : LockActionGroups.forVocabulary(vocabulary)) names.add(group[0]);
        return names;
    }

    private static List<String> actionsIn(List<String> vocabulary, String heading) {
        for (String[] group : LockActionGroups.forVocabulary(vocabulary)) {
            if (group[0].equals(heading)) {
                return new ArrayList<>(List.of(group).subList(1, group.length));
            }
        }
        return List.of();
    }

    @Test
    void theItemVocabularyKeepsTheBroadTradeAction() {
        List<String> names = headings(LockActions.ITEM);
        assertEquals(List.of("item", "block", "output"), names,
                "three headings and no more - the fourth was the two sides of an offer, and it"
                        + " went with them");
        assertTrue(actionsIn(LockActions.ITEM, "item").contains("trade"),
                "the broad rule stayed: 'nobody trades with this at all', beside the other ways"
                        + " of acquiring something");
    }

    @Test
    void theFluidVocabularyKeepsIngredientAndTheOthersDrop() {
        assertEquals(List.of("item", "block", "output"), headings(LockActions.FLUID));
        assertTrue(actionsIn(LockActions.FLUID, "output").contains("ingredient"));
        assertFalse(actionsIn(LockActions.ITEM, "output").contains("ingredient"),
                "ingredient exists only for fluids");
        assertFalse(actionsIn(LockActions.FLUID, "item").contains("attack"),
                "a fluid is never swung");
    }

    @Test
    void everyActionAnyCategoryOffersHasSomewhereToBeDrawn() {
        List<String> laidOut = LockActionGroups.laidOutActions();
        List<String> orphans = new ArrayList<>();
        for (String action : LockActions.KNOWN) {
            if (!laidOut.contains(action)) orphans.add(action);
        }
        assertTrue(orphans.isEmpty(),
                "these actions are in a category's vocabulary but in no group, so an entry can be"
                        + " narrowed to them in a stage file and never switched off in the editor: "
                        + orphans);
    }

    @Test
    void aVocabularyNothingIsLaidOutForGetsNoGroupsRatherThanEmptyHeadings() {
        assertTrue(LockActionGroups.forVocabulary(List.of("something:nobody:laid:out")).isEmpty());
        assertTrue(LockActionGroups.forVocabulary(List.of()).isEmpty());
    }
}
