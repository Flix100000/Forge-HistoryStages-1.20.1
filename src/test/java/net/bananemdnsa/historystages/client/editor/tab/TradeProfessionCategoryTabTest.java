package net.bananemdnsa.historystages.client.editor.tab;

import java.util.List;

import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.TradeProfessionEntry;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The level narrowing lives beside the rows rather than in them, which is the arrangement that
 * loses data quietly: the row list is what the editor draws and saves, and anything kept next to
 * it has to be carried along by hand at every step.
 *
 * <p>So the steps are what these check — loading, saving, and removing a row.
 */
class TradeProfessionCategoryTabTest {

    @SuppressWarnings("unchecked")
    private static TradeProfessionCategoryTab tab() {
        LockCategory<TradeProfessionEntry> category =
                (LockCategory<TradeProfessionEntry>)
                        LockCategories.byId("historystages:trade_professions");
        return new TradeProfessionCategoryTab(category, (onSelect, added) -> null, () -> { });
    }

    private static StageEntry stageWith(TradeProfessionEntry... entries) {
        StageEntry stage = new StageEntry();
        stage.setTradeProfessionEntries(List.of(entries));
        return stage;
    }

    @Test
    void openingAStageShowsTheRowsAndTheirNarrowing() {
        TradeProfessionCategoryTab tab = tab();
        tab.load(stageWith(new TradeProfessionEntry("minecraft:cleric"),
                new TradeProfessionEntry("minecraft:librarian", List.of("4", "5"))));

        assertEquals(List.of("minecraft:cleric", "minecraft:librarian"), tab.entries());
        assertNull(tab.levelsFor("minecraft:cleric"));
        assertEquals(List.of("4", "5"), tab.levelsFor("minecraft:librarian"));
    }

    @Test
    void savingCarriesTheNarrowingBackIntoTheStage() {
        TradeProfessionCategoryTab tab = tab();
        tab.load(stageWith(new TradeProfessionEntry("minecraft:librarian")));
        tab.setLevelsFor("minecraft:librarian", List.of("4", "5"));

        StageEntry saved = new StageEntry();
        tab.store(saved);

        List<TradeProfessionEntry> stored = saved.getTradeProfessionEntries();
        assertEquals(1, stored.size());
        assertEquals(List.of("4", "5"), stored.get(0).getLevels(),
                "the narrowing is not in the row list, so a store that only walks the rows drops"
                        + " it and the stage comes back gating every level");
    }

    @Test
    void theLevelsComeBackInMerchantOrderWhateverOrderTheyWereClicked() {
        TradeProfessionCategoryTab tab = tab();
        tab.load(stageWith(new TradeProfessionEntry("minecraft:librarian")));
        tab.setLevelsFor("minecraft:librarian", List.of("5", "2", "4"));

        assertEquals(List.of("2", "4", "5"), tab.levelsFor("minecraft:librarian"),
                "a stage file should read in the order a merchant climbs the levels, not in the"
                        + " order somebody happened to click them");
    }

    @Test
    void namingEveryLevelIsStoredAsNoNarrowingAtAll() {
        TradeProfessionCategoryTab tab = tab();
        tab.load(stageWith(new TradeProfessionEntry("minecraft:librarian", List.of("4"))));
        tab.setLevelsFor("minecraft:librarian", TradeProfessionEntry.ALL_LEVELS);

        assertNull(tab.levelsFor("minecraft:librarian"));

        StageEntry saved = new StageEntry();
        tab.store(saved);
        assertTrue(!saved.getTradeProfessionEntries().get(0).hasLevels(),
                "a profession gating all five levels is a bare profession; writing the list out"
                        + " would change the file's shape for no change in meaning");
    }

    @Test
    void namingNoLevelIsAlsoNoNarrowing() {
        TradeProfessionCategoryTab tab = tab();
        tab.load(stageWith(new TradeProfessionEntry("minecraft:librarian", List.of("4"))));
        tab.setLevelsFor("minecraft:librarian", List.of());

        assertNull(tab.levelsFor("minecraft:librarian"),
                "an entry gating nothing would sit in the list looking like a lock and do"
                        + " nothing, and nobody could tell why");
    }

    @Test
    void removingARowTakesItsNarrowingWithIt() {
        TradeProfessionCategoryTab tab = tab();
        tab.load(stageWith(new TradeProfessionEntry("minecraft:cleric", List.of("1")),
                new TradeProfessionEntry("minecraft:librarian", List.of("4"))));

        tab.removeAt(0);

        assertEquals(List.of("minecraft:librarian"), tab.entries());
        assertNull(tab.levelsFor("minecraft:cleric"),
                "a narrowing left behind would attach itself to the profession again the moment"
                        + " somebody re-added it, which reads as the editor inventing a lock");
        assertEquals(List.of("4"), tab.levelsFor("minecraft:librarian"),
                "and the row that stayed must keep its own");
    }

    @Test
    void aNarrowedRowSaysSoOnTheRowItself() {
        TradeProfessionCategoryTab tab = tab();
        tab.load(stageWith(new TradeProfessionEntry("minecraft:cleric"),
                new TradeProfessionEntry("minecraft:librarian", List.of("4", "5"))));

        assertNull(tab.badgeText(0), "a profession gating every level is the ordinary case");
        assertEquals("[4,5]", tab.badgeText(1),
                "otherwise the narrowing is invisible until you right-click the row, and a lock"
                        + " that looks like it covers everything is the wrong kind of surprise");
    }

    @Test
    void loadingTwiceDoesNotKeepTheFirstStagesNarrowing() {
        TradeProfessionCategoryTab tab = tab();
        tab.load(stageWith(new TradeProfessionEntry("minecraft:librarian", List.of("4"))));
        tab.load(stageWith(new TradeProfessionEntry("minecraft:librarian")));

        assertNull(tab.levelsFor("minecraft:librarian"));
    }
}
