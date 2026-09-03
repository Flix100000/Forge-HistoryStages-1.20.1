package net.bananemdnsa.historystages.data.lock;

import net.bananemdnsa.historystages.data.lock.FluidIndexStaleness.Action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Whether a staleness signal is worth a full re-scan.
 *
 * <p>Re-scanning means encoding every recipe in the pack through its own serialiser. While the
 * index existed only for gated fluids that was rare. Once the editor keeps it alive, a stage
 * change would trigger it — and saving a stage is what one does constantly in the editor. Stages
 * never change what a recipe contains, only whether the index is wanted at all.
 */
class FluidIndexStalenessTest {

    @Test
    void nothingStaleMeansNoWork() {
        assertEquals(Action.NOTHING, FluidIndexStaleness.decide(false, false, true, true));
        assertEquals(Action.NOTHING, FluidIndexStaleness.decide(false, false, false, true));
    }

    @Test
    void changedRecipesAlwaysForceARescan() {
        assertEquals(Action.REBUILD, FluidIndexStaleness.decide(true, false, true, true));
        assertEquals(Action.REBUILD, FluidIndexStaleness.decide(true, false, false, true));
    }

    @Test
    void aStageChangeKeepsAScanOfUnchangedRecipes() {
        // The regression this whole class exists to prevent: every save re-scanning the pack.
        assertEquals(Action.NOTHING, FluidIndexStaleness.decide(false, true, true, true));
    }

    @Test
    void aStageChangeStillBuildsTheFirstIndex() {
        // A pack adding its first fluid entry in the editor has to get one built.
        assertEquals(Action.REBUILD, FluidIndexStaleness.decide(false, true, false, true));
    }

    @Test
    void anUnwantedIndexIsDropped() {
        assertEquals(Action.DROP, FluidIndexStaleness.decide(false, true, true, false));
        assertEquals(Action.DROP, FluidIndexStaleness.decide(true, false, true, false));
    }

    @Test
    void anUnwantedAbsentIndexNeedsNothing() {
        assertEquals(Action.NOTHING, FluidIndexStaleness.decide(true, true, false, false));
    }
}
