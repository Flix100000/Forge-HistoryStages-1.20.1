package net.bananemdnsa.historystages.data.lock.category;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualPhaseIndexTest {

    private static StageEntry withItems(String... itemIds) {
        StageEntry stage = new StageEntry();
        stage.setItems(List.of(itemIds));
        return stage;
    }

    private static Map<String, StageEntry> stages(String id, StageEntry entry) {
        Map<String, StageEntry> map = new LinkedHashMap<>();
        map.put(id, entry);
        return map;
    }

    @Test
    void anItemInBothScopesIsDualPhase() {
        DualPhaseIndex index = DualPhaseIndex.build(
                stages("bronze", withItems("minecraft:stone")),
                stages("quest", withItems("minecraft:stone")));

        assertEquals(Set.of("bronze"), index.global("historystages:items").get("minecraft:stone"));
        assertEquals(Set.of("quest"), index.individual("historystages:items").get("minecraft:stone"));
    }

    @Test
    void anItemGatedOnlyGloballyIsNotDualPhase() {
        DualPhaseIndex index = DualPhaseIndex.build(stages("bronze", withItems("minecraft:stone")), Map.of());
        assertTrue(index.global("historystages:items").isEmpty());
        assertTrue(index.individual("historystages:items").isEmpty());
    }

    @Test
    void anItemGatedOnlyIndividuallyIsNotDualPhase() {
        DualPhaseIndex index = DualPhaseIndex.build(Map.of(), stages("quest", withItems("minecraft:stone")));
        assertTrue(index.global("historystages:items").isEmpty());
        assertTrue(index.individual("historystages:items").isEmpty());
    }

    @Test
    void twoGlobalStagesGatingTheSameItemBothShowUp() {
        Map<String, StageEntry> globals = new LinkedHashMap<>();
        globals.put("bronze", withItems("minecraft:stone"));
        globals.put("iron", withItems("minecraft:stone"));

        DualPhaseIndex index = DualPhaseIndex.build(globals, stages("quest", withItems("minecraft:stone")));

        assertEquals(Set.of("bronze", "iron"), index.global("historystages:items").get("minecraft:stone"));
    }

    @Test
    void aCategoryThatOptsOutIsNeverIndexed() {
        StageEntry global = new StageEntry();
        global.setRecipes(List.of("minecraft:stone_stairs"));
        StageEntry individual = new StageEntry();
        individual.setRecipes(List.of("minecraft:stone_stairs"));

        DualPhaseIndex index = DualPhaseIndex.build(stages("bronze", global), stages("quest", individual));

        assertTrue(index.global("historystages:recipes").isEmpty(),
                "recipes never took part in dual-phase detection");
    }

    @Test
    void anUnknownCategoryReadsAsEmptyRatherThanNull() {
        DualPhaseIndex index = DualPhaseIndex.build(Map.of(), Map.of());
        assertTrue(index.global("mymod:villagertrades").isEmpty());
        assertTrue(index.individual("mymod:villagertrades").isEmpty());
    }

    @Test
    void anItemEntryWithNbtStillCountsByItsId() {
        StageEntry global = new StageEntry();
        global.setItemEntries(List.of(new ItemEntry("minecraft:stone")));
        StageEntry individual = new StageEntry();
        individual.setItemEntries(List.of(new ItemEntry("minecraft:stone")));

        DualPhaseIndex index = DualPhaseIndex.build(stages("bronze", global), stages("quest", individual));

        assertEquals(Set.of("bronze"), index.global("historystages:items").get("minecraft:stone"));
    }

    @Test
    void aGlobalSpawnLockWithoutSourcesCountsAsAnAttackLockOverlap() {
        StageEntry global = new StageEntry();
        global.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));

        StageEntry individual = new StageEntry();
        individual.getEntities().setAttacklock(List.of("minecraft:zombie"));

        DualPhaseIndex index = DualPhaseIndex.build(stages("bronze", global), stages("quest", individual));

        assertEquals(Set.of("bronze"),
                index.global("historystages:attacklock").get("minecraft:zombie"));
    }

    @Test
    void anIndividualSpawnLockDoesNotCountAsAnAttackLockOverlap() {
        StageEntry global = new StageEntry();
        global.getEntities().setAttacklock(List.of("minecraft:zombie"));

        StageEntry individual = new StageEntry();
        individual.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));

        DualPhaseIndex index = DualPhaseIndex.build(stages("bronze", global), stages("quest", individual));

        assertTrue(index.individual("historystages:attacklock").isEmpty(),
                "the individual side has never absorbed spawn locks");
    }

    @Test
    void eachOverlapProducesTheLegacyLoadingMessage() {
        DualPhaseIndex index = DualPhaseIndex.build(
                stages("bronze", withItems("minecraft:stone")),
                stages("quest", withItems("minecraft:stone")));

        assertEquals(List.of("Individual stage 'quest' item 'minecraft:stone' also in global"
                        + " stage(s) [bronze] — dual-phase lock registered."),
                index.messages());
    }

    @Test
    void noOverlapProducesNoMessages() {
        DualPhaseIndex index = DualPhaseIndex.build(stages("bronze", withItems("minecraft:stone")), Map.of());
        assertTrue(index.messages().isEmpty());
    }
}
