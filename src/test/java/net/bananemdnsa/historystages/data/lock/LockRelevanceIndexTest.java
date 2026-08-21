package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the registry-free half of the index. The tag path needs a live item registry and is
 * left to in-game testing.
 */
class LockRelevanceIndexTest {

    private static StageEntry stageWithItems(String... itemIds) {
        StageEntry entry = new StageEntry();
        List<ItemEntry> items = new ArrayList<>();
        for (String id : itemIds) items.add(new ItemEntry(id));
        entry.setItemEntries(items);
        return entry;
    }

    private static StageEntry stageWithMods(String... modIds) {
        StageEntry entry = new StageEntry();
        List<NamedLockEntry> mods = new ArrayList<>();
        for (String id : modIds) mods.add(new NamedLockEntry(id));
        entry.setModEntries(mods);
        return entry;
    }

    private static Map<String, StageEntry> stages(Object... idsAndEntries) {
        Map<String, StageEntry> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndEntries.length; i += 2) {
            map.put((String) idsAndEntries[i], (StageEntry) idsAndEntries[i + 1]);
        }
        return map;
    }

    @Test
    void noStagesYieldsTheEmptyIndex() {
        assertSame(LockRelevanceIndex.EMPTY, LockRelevanceIndex.build(Map.of()));
        assertSame(LockRelevanceIndex.EMPTY, LockRelevanceIndex.build(null));
    }

    @Test
    void stagesWithoutItemContentYieldTheEmptyIndex() {
        assertSame(LockRelevanceIndex.EMPTY,
                LockRelevanceIndex.build(stages("bronze", new StageEntry())));
    }

    @Test
    void anUnstagedItemHasNoCandidates() {
        LockRelevanceIndex index = LockRelevanceIndex.build(
                stages("bronze", stageWithItems("minecraft:iron_ingot")));

        assertTrue(index.candidateStagesByIdOrMod("minecraft:diamond", "minecraft").isEmpty());
    }

    @Test
    void aStagedItemFindsOnlyItsOwnStages() {
        LockRelevanceIndex index = LockRelevanceIndex.build(stages(
                "bronze", stageWithItems("minecraft:iron_ingot"),
                "iron", stageWithItems("minecraft:iron_ingot", "minecraft:anvil"),
                "steel", stageWithItems("minecraft:diamond")));

        assertEquals(List.of("bronze", "iron"),
                new ArrayList<>(index.candidateStagesByIdOrMod("minecraft:iron_ingot", "minecraft")));
    }

    @Test
    void aModEntryMakesEveryItemOfThatModACandidate() {
        LockRelevanceIndex index = LockRelevanceIndex.build(
                stages("modded", stageWithMods("create")));

        assertEquals(List.of("modded"),
                new ArrayList<>(index.candidateStagesByIdOrMod("create:cogwheel", "create")));
        assertTrue(index.candidateStagesByIdOrMod("minecraft:diamond", "minecraft").isEmpty());
    }

    @Test
    void itemAndModHitsAreMergedWithoutDuplicates() {
        StageEntry both = stageWithItems("create:cogwheel");
        both.setModEntries(List.of(new NamedLockEntry("create")));

        LockRelevanceIndex index = LockRelevanceIndex.build(stages(
                "overlap", both,
                "byMod", stageWithMods("create")));

        assertEquals(List.of("overlap", "byMod"),
                new ArrayList<>(index.candidateStagesByIdOrMod("create:cogwheel", "create")));
    }

    @Test
    void aStageListingAnItemTwiceIsReportedOnce() {
        LockRelevanceIndex index = LockRelevanceIndex.build(
                stages("bronze", stageWithItems("minecraft:iron_ingot", "minecraft:iron_ingot")));

        assertEquals(List.of("bronze"),
                new ArrayList<>(index.candidateStagesByIdOrMod("minecraft:iron_ingot", "minecraft")));
    }

    @Test
    void nbtEntriesStillRegisterTheirItemId() {
        StageEntry entry = new StageEntry();
        com.google.gson.JsonObject nbt = new com.google.gson.JsonObject();
        nbt.addProperty("Damage", 0);
        entry.setItemEntries(List.of(new ItemEntry("minecraft:diamond_sword", nbt, null)));

        LockRelevanceIndex index = LockRelevanceIndex.build(stages("bronze", entry));

        // The index does not evaluate NBT — it must still offer the stage so the real check runs.
        assertEquals(List.of("bronze"),
                new ArrayList<>(index.candidateStagesByIdOrMod("minecraft:diamond_sword", "minecraft")));
    }
}
