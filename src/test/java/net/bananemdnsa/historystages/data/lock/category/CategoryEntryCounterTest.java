package net.bananemdnsa.historystages.data.lock.category;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryEntryCounterTest {

    record Relic(String id) {}

    @AfterEach
    void reset() {
        LockCategories.resetForTesting();
    }

    @Test
    void anEmptyStageCountsNothing() {
        assertEquals(0, CategoryEntryCounter.totalEntries(new StageEntry()));
    }

    @Test
    void everyBuiltInCategoryIsCounted() {
        StageEntry stage = new StageEntry();
        stage.setItemEntries(List.of(new ItemEntry("minecraft:stone"), new ItemEntry("minecraft:dirt")));
        stage.setDimensions(List.of("minecraft:the_nether"));
        stage.setRecipes(List.of("minecraft:stone_stairs"));

        assertEquals(4, CategoryEntryCounter.totalEntries(stage));
    }

    /**
     * The old hand-written sums both forgot this one, which is precisely the failure asking the
     * registry is meant to make impossible.
     */
    @Test
    void modExceptionsAreCountedToo() {
        StageEntry stage = new StageEntry();
        stage.setModExceptionEntries(List.of(new ItemEntry("minecraft:stone")));

        assertEquals(1, CategoryEntryCounter.totalEntries(stage));
    }

    @Test
    void aRegisteredAddonCategoryIsCounted() {
        AddonLockCategory<Relic> relics = AddonLockCategory.<Relic>builder("hsdemo:relics")
                .tabLangKey("editor.mymod.tab.relics")
                .tooltipLangKey("editor.mymod.tooltip.relics")
                .storage(CategoryStorage.gson(Relic.class))
                .build();
        LockCategories.register(relics);

        StageEntry stage = new StageEntry();
        relics.write(stage, List.of(new Relic("a"), new Relic("b")));

        assertEquals(2, CategoryEntryCounter.totalEntries(stage));
    }

    /**
     * The entries are really there — the stage gates them again the moment the addon comes back.
     * Reporting a smaller number is the kind of lie that makes someone delete data.
     */
    @Test
    void anUninstalledAddonsEntriesAreStillCounted() {
        StageEntry stage = new StageEntry();
        JsonArray raw = new JsonArray();
        raw.add(new JsonPrimitive("a"));
        raw.add(new JsonPrimitive("b"));
        raw.add(new JsonPrimitive("c"));
        stage.setAddonEntries("gonemod:things", raw);

        assertEquals(3, CategoryEntryCounter.totalEntries(stage));
    }

    @Test
    void anInstalledAddonIsNotCountedTwice() {
        AddonLockCategory<Relic> relics = AddonLockCategory.<Relic>builder("hsdemo:relics")
                .tabLangKey("editor.mymod.tab.relics")
                .tooltipLangKey("editor.mymod.tooltip.relics")
                .storage(CategoryStorage.gson(Relic.class))
                .build();
        LockCategories.register(relics);

        StageEntry stage = new StageEntry();
        relics.write(stage, List.of(new Relic("a"), new Relic("b")));

        assertEquals(2, CategoryEntryCounter.totalEntries(stage));
    }

    @Test
    void malformedAddonDataDoesNotThrow() {
        StageEntry stage = new StageEntry();
        stage.setAddonEntries("gonemod:things", new JsonPrimitive("not a list"));

        assertEquals(0, CategoryEntryCounter.totalEntries(stage));
    }

    @Test
    void theCountSurvivesAJsonRoundTrip() {
        StageEntry stage = new StageEntry();
        stage.setItemEntries(List.of(new ItemEntry("minecraft:stone")));
        JsonArray raw = new JsonArray();
        raw.add(new JsonPrimitive("a"));
        stage.setAddonEntries("gonemod:things", raw);

        StageEntry reloaded = new Gson().fromJson(stage.toJson(), StageEntry.class);

        assertEquals(2, CategoryEntryCounter.totalEntries(reloaded));
    }
}
