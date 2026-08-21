package net.bananemdnsa.historystages.data.lock.category;

import java.util.List;

import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryMatcherTest {

    /** What the addon stores in the stage file. */
    record Trade(String soldItem) {}

    /** What the addon has in hand at runtime — deliberately a different type. */
    record Offer(String soldItem, int price) {}

    private static AddonLockCategory<Trade> withMatcher() {
        return AddonLockCategory.<Trade>builder("mymod:villagertrades")
                .tabLangKey("editor.mymod.tab.trades")
                .tooltipLangKey("editor.mymod.tooltip.trades")
                .storage(CategoryStorage.gson(Trade.class))
                .matcher(Offer.class, (Trade entry, Offer offer) -> entry.soldItem().equals(offer.soldItem()))
                .build();
    }

    private static AddonLockCategory<Trade> withoutMatcher() {
        return AddonLockCategory.<Trade>builder("mymod:plain")
                .tabLangKey("editor.mymod.tab.plain")
                .tooltipLangKey("editor.mymod.tooltip.plain")
                .storage(CategoryStorage.gson(Trade.class))
                .build();
    }

    @Test
    void aMatchingSubjectMatches() {
        assertTrue(withMatcher().matches(new Trade("minecraft:emerald"), new Offer("minecraft:emerald", 3)));
    }

    @Test
    void aDifferentSubjectDoesNotMatch() {
        assertFalse(withMatcher().matches(new Trade("minecraft:emerald"), new Offer("minecraft:diamond", 3)));
    }

    /**
     * An addon that never registered a matcher cannot gate anything at runtime — it can still
     * store entries and show them in the editor. Matching nothing is the safe answer; matching
     * everything would lock the game up.
     */
    @Test
    void aCategoryWithoutAMatcherMatchesNothing() {
        assertFalse(withoutMatcher().matches(new Trade("minecraft:emerald"), new Offer("minecraft:emerald", 3)));
    }

    @Test
    void aSubjectOfTheWrongTypeIsRejectedRatherThanCastBlindly() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> withMatcher().matches(new Trade("minecraft:emerald"), "not an offer"));
        assertTrue(thrown.getMessage().contains("mymod:villagertrades"),
                "the error should name the category so the addon dev knows who to blame: "
                        + thrown.getMessage());
    }

    @Test
    void aNullSubjectMatchesNothing() {
        assertFalse(withMatcher().matches(new Trade("minecraft:emerald"), null));
    }

    @Test
    void builtInCategoriesMatchNothingByDefault() {
        assertFalse(matchesErased(LockCategories.byId("historystages:items"), "anything"));
    }

    @SuppressWarnings("unchecked")
    private static boolean matchesErased(LockCategory<?> category, Object subject) {
        return ((LockCategory<Object>) category).matches(null, subject);
    }

    @Test
    void aCategoryWithAMatcherStillStoresNormally() {
        AddonLockCategory<Trade> category = withMatcher();
        StageEntry stage = new StageEntry();
        category.write(stage, List.of(new Trade("minecraft:emerald")));

        assertTrue(category.read(stage).contains(new Trade("minecraft:emerald")));
    }
}
