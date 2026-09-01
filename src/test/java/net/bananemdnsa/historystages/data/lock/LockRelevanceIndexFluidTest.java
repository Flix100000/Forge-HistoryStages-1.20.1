package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.data.FluidEntry;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The narrowing step for fluids.
 *
 * <p>This is the piece where a mistake is silent and total. The index decides which stages are
 * even asked; a stage it fails to name is never consulted, and the thing it gates comes back
 * unlocked with no error anywhere. A stage that gates only a fluid mentions no item id at all,
 * so it is exactly the shape the item-and-mod narrowing would drop.
 */
class LockRelevanceIndexFluidTest {

    private static StageEntry stageWithFluids(String... fluidIds) {
        StageEntry entry = new StageEntry();
        List<FluidEntry> fluids = new ArrayList<>();
        for (String id : fluidIds) fluids.add(new FluidEntry(id));
        entry.setFluidEntries(fluids);
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
    void aStageThatGatesOnlyAFluidIsStillFound() {
        LockRelevanceIndex index = LockRelevanceIndex.build(
                stages("bronze", stageWithFluids("minecraft:lava")));

        assertEquals(List.of("bronze"), new ArrayList<>(index.candidateStagesByIdOrMod(
                "minecraft:lava_bucket", "minecraft", "minecraft:lava")));
    }

    @Test
    void aStageThatGatesOnlyAFluidIsNotTheEmptyIndex() {
        assertTrue(LockRelevanceIndex.build(stages("bronze", stageWithFluids("minecraft:lava")))
                .isEmpty() == false);
    }

    @Test
    void aContainerOfAnUngatedFluidHasNoCandidates() {
        LockRelevanceIndex index = LockRelevanceIndex.build(
                stages("bronze", stageWithFluids("minecraft:lava")));

        assertTrue(index.candidateStagesByIdOrMod(
                "minecraft:water_bucket", "minecraft", "minecraft:water").isEmpty());
    }

    @Test
    void anEmptyContainerCarriesNoFluidAndFindsNothing() {
        LockRelevanceIndex index = LockRelevanceIndex.build(
                stages("bronze", stageWithFluids("minecraft:lava")));

        assertTrue(index.candidateStagesByIdOrMod("minecraft:bucket", "minecraft", null).isEmpty());
    }

    @Test
    void theItemAndTheFluidSourceAreMergedWithoutDuplicates() {
        StageEntry both = stageWithFluids("minecraft:lava");
        both.setItemEntries(List.of(new ItemEntry("minecraft:lava_bucket")));

        LockRelevanceIndex index = LockRelevanceIndex.build(stages("bronze", both));

        assertEquals(List.of("bronze"), new ArrayList<>(index.candidateStagesByIdOrMod(
                "minecraft:lava_bucket", "minecraft", "minecraft:lava")));
    }

    @Test
    void twoStagesGatingTheSameFluidAreBothCandidates() {
        LockRelevanceIndex index = LockRelevanceIndex.build(stages(
                "bronze", stageWithFluids("minecraft:lava"),
                "iron", stageWithFluids("minecraft:lava")));

        assertEquals(List.of("bronze", "iron"), new ArrayList<>(index.candidateStagesByIdOrMod(
                "minecraft:lava_bucket", "minecraft", "minecraft:lava")));
    }

    @Test
    void aStageWithNothingAtAllStillYieldsTheEmptyIndex() {
        assertSame(LockRelevanceIndex.EMPTY,
                LockRelevanceIndex.build(stages("bronze", new StageEntry())));
    }
}
