package net.bananemdnsa.historystages.data.lock.category;

import java.util.List;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonLockCategoryTest {

    /** Stand-in for whatever an addon would store. */
    record Trade(String id, int price) {}

    private static AddonLockCategory<Trade> tradeCategory() {
        return category("mymod:villagertrades");
    }

    private static AddonLockCategory<Trade> category(String id) {
        return AddonLockCategory.<Trade>builder(id)
                .tabLangKey("editor.mymod.tab.villagertrades")
                .tooltipLangKey("editor.mymod.tooltip.villagertrades")
                .storage(CategoryStorage.gson(Trade.class))
                .build();
    }

    @Test
    void entriesRoundTripThroughTheStage() {
        AddonLockCategory<Trade> category = tradeCategory();
        StageEntry stage = new StageEntry();

        category.write(stage, List.of(new Trade("minecraft:emerald", 3)));

        assertEquals(List.of(new Trade("minecraft:emerald", 3)), category.read(stage));
    }

    @Test
    void entriesSurviveAFullJsonRoundTrip() {
        AddonLockCategory<Trade> category = tradeCategory();
        StageEntry stage = new StageEntry();
        category.write(stage, List.of(new Trade("minecraft:emerald", 3)));

        StageEntry reloaded = new Gson().fromJson(stage.toJson(), StageEntry.class);

        assertEquals(List.of(new Trade("minecraft:emerald", 3)), category.read(reloaded));
    }

    @Test
    void anEmptyCategoryReadsAsAnEmptyListNotNull() {
        assertTrue(tradeCategory().read(new StageEntry()).isEmpty());
    }

    @Test
    void writingAnEmptyListRemovesTheCategoryFromTheStage() {
        AddonLockCategory<Trade> category = tradeCategory();
        StageEntry stage = new StageEntry();
        category.write(stage, List.of(new Trade("minecraft:emerald", 3)));

        category.write(stage, List.of());

        assertTrue(stage.addonCategoryIds().isEmpty(),
                "an emptied category should not leave a stub behind in the file");
    }

    @Test
    void twoAddonCategoriesDoNotDisturbEachOther() {
        AddonLockCategory<Trade> trades = tradeCategory();
        AddonLockCategory<Trade> other = category("othermod:things");

        StageEntry stage = new StageEntry();
        trades.write(stage, List.of(new Trade("a", 1)));
        other.write(stage, List.of(new Trade("b", 2)));

        assertEquals(List.of(new Trade("a", 1)), trades.read(stage));
        assertEquals(List.of(new Trade("b", 2)), other.read(stage));
    }

    @Test
    void readingSomebodyElsesGarbageYieldsAnEmptyListRatherThanThrowing() {
        AddonLockCategory<Trade> category = tradeCategory();
        StageEntry stage = new StageEntry();
        stage.setAddonEntries("mymod:villagertrades", new com.google.gson.JsonPrimitive("not a list"));

        assertTrue(category.read(stage).isEmpty(),
                "a malformed entry in someone's hand-edited file must not crash stage loading");
    }

    @Test
    void anAddonCategoryOptsOutOfDualPhaseUnlessItSaysOtherwise() {
        AddonLockCategory<Trade> category = tradeCategory();
        StageEntry stage = new StageEntry();
        category.write(stage, List.of(new Trade("minecraft:emerald", 3)));

        assertTrue(category.globalDualPhaseIds(stage).isEmpty());
        assertTrue(category.individualDualPhaseIds(stage).isEmpty());
        assertEquals("", category.dualPhaseLabel());
    }

    @Test
    void anIdWithoutANamespaceIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> AddonLockCategory.<Trade>builder("villagertrades")
                        .tabLangKey("a").tooltipLangKey("b")
                        .storage(CategoryStorage.gson(Trade.class))
                        .build());
        assertTrue(thrown.getMessage().contains("namespace"),
                "the error should say what is wrong: " + thrown.getMessage());
    }

    @Test
    void theHistorystagesNamespaceIsReservedForBuiltIns() {
        assertThrows(IllegalArgumentException.class,
                () -> AddonLockCategory.<Trade>builder("historystages:sneaky")
                        .tabLangKey("a").tooltipLangKey("b")
                        .storage(CategoryStorage.gson(Trade.class))
                        .build());
    }

    @Test
    void aMissingStorageIsRejectedAtBuildTime() {
        assertThrows(IllegalStateException.class,
                () -> AddonLockCategory.<Trade>builder("mymod:things")
                        .tabLangKey("a").tooltipLangKey("b")
                        .build());
    }
}
