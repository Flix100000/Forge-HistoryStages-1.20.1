package net.bananemdnsa.historystages.data.lock.category;

import java.util.List;

import net.bananemdnsa.historystages.api.lock.LockActions;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.FluidEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything about the fluid category that can be pinned without a live registry.
 *
 * <p>The matching itself takes a real {@code ItemStack} — the whole point is that a bucket
 * nobody listed comes back locked — and is pinned in {@code gametest/FluidLockTests} instead.
 */
class FluidCategoryTest {

    @SuppressWarnings("unchecked")
    private static LockCategory<FluidEntry> fluids() {
        LockCategory<?> found = LockCategories.byId("historystages:fluids");
        assertNotNull(found, "no built-in category registered under historystages:fluids");
        return (LockCategory<FluidEntry>) found;
    }

    private static StageEntry gating(String fluidId) {
        StageEntry stage = new StageEntry();
        stage.setFluidEntries(List.of(new FluidEntry(fluidId)));
        return stage;
    }

    @Test
    void itIsRegistered() {
        assertEquals("historystages:fluids", fluids().id());
    }

    @Test
    void itReadsAndWritesTheStageField() {
        StageEntry stage = gating("minecraft:lava");
        assertEquals(1, fluids().read(stage).size());

        fluids().write(stage, List.of(new FluidEntry("minecraft:water")));
        assertEquals("minecraft:water", stage.getFluidEntries().get(0).getId());
    }

    @Test
    void itOffersTheSevenFluidActions() {
        assertEquals(LockActions.FLUID, fluids().lockActions());
    }

    /**
     * Unlike spawn locks, a fluid question always has a player behind it: somebody is holding
     * the container. Nothing here forces the global-only answer.
     */
    @Test
    void bothScopesAreSupported() {
        assertTrue(fluids().supportedScopes().contains(StageScope.GLOBAL));
        assertTrue(fluids().supportedScopes().contains(StageScope.INDIVIDUAL));
    }

    @Test
    void itIndexesUnderItsFluidIds() {
        assertEquals(List.of("minecraft:lava"), fluids().indexKeys(gating("minecraft:lava")));
    }

    @Test
    void itTakesPartInDualPhaseDetectionOnBothSides() {
        StageEntry stage = gating("minecraft:lava");
        assertEquals(List.of("minecraft:lava"), fluids().globalDualPhaseIds(stage));
        assertEquals(List.of("minecraft:lava"), fluids().individualDualPhaseIds(stage));
    }

    @Test
    void aStageWithNoFluidsIndexesNothing() {
        assertEquals(List.of(), fluids().indexKeys(new StageEntry()));
    }

    /**
     * The neighbours keep the ten. The fluid category was deliberately built as a class of its
     * own rather than by widening {@code Simple}, precisely so this stays true — but that is a
     * decision someone could undo, and this is what would notice.
     */
    @Test
    void theOtherSimpleCategoriesKeepTheirTenActions() {
        for (String id : List.of("historystages:items", "historystages:recipes",
                "historystages:dimensions", "historystages:structures", "historystages:biomes")) {
            LockCategory<?> category = LockCategories.byId(id);
            assertNotNull(category, id + " went missing");
            assertEquals(LockActions.ITEM, category.lockActions(), id + " lost its action list");
        }
    }

    @Test
    void theOtherSimpleCategoriesKeepTheirDualPhaseLabels() {
        assertEquals("item", LockCategories.byId("historystages:items").dualPhaseLabel());
        assertEquals("recipe", LockCategories.byId("historystages:recipes").dualPhaseLabel());
        assertEquals("dimension", LockCategories.byId("historystages:dimensions").dualPhaseLabel());
        assertEquals("fluid", LockCategories.byId("historystages:fluids").dualPhaseLabel());
    }
}
