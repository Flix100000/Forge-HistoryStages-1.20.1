package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.List;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryRoundTripTest {

    @Test
    void everyCategoryStartsEmptyOnAFreshStage() {
        StageEntry stage = new StageEntry();
        for (LockCategory<?> category : LockCategories.all()) {
            assertTrue(category.read(stage).isEmpty(),
                    category.id() + " is not empty on a fresh stage");
        }
    }

    @Test
    void everyCategoryReadsBackWhatItWrote() {
        for (LockCategory<?> category : LockCategories.all()) {
            StageEntry stage = new StageEntry();
            int written = writeSample(category, stage);
            assertEquals(written, category.read(stage).size(),
                    category.id() + " did not read back what was written");
        }
    }

    @Test
    void writingOneCategoryLeavesTheOthersAlone() {
        for (LockCategory<?> target : LockCategories.all()) {
            StageEntry stage = new StageEntry();
            writeSample(target, stage);

            for (LockCategory<?> other : LockCategories.all()) {
                if (other.id().equals(target.id())) continue;
                assertTrue(other.read(stage).isEmpty(),
                        "writing " + target.id() + " also filled " + other.id());
            }
        }
    }

    @Test
    void dualPhaseIdsAreEmptyForTheCategoriesThatOptOut() {
        for (String optedOut : List.of("historystages:mod_exceptions",
                                       "historystages:spawnlock")) {
            StageEntry stage = new StageEntry();
            LockCategory<?> category = LockCategories.byId(optedOut);
            writeSample(category, stage);
            assertTrue(category.globalDualPhaseIds(stage).isEmpty()
                            && category.individualDualPhaseIds(stage).isEmpty(),
                    optedOut + " should not take part in dual-phase detection");
            assertEquals("", category.dualPhaseLabel(),
                    optedOut + " should not declare a dual-phase label");
        }
    }

    @Test
    void recipesTakePartInDualPhaseDetection() {
        // They did not until 6.0.0, because there was no second phase to overlap with. Now that a
        // station can gate per player, the same recipe can be gated globally and individually at
        // once — which is the case the whole check exists for.
        StageEntry stage = new StageEntry();
        LockCategory<?> recipes = LockCategories.byId("historystages:recipes");
        writeSample(recipes, stage);

        assertFalse(recipes.globalDualPhaseIds(stage).isEmpty(),
                "a global stage's recipes belong in the overlap scan");
        assertFalse(recipes.individualDualPhaseIds(stage).isEmpty(),
                "an individual stage's recipes belong in the overlap scan");
        assertEquals("recipe", recipes.dualPhaseLabel(),
                "the label is printed in the dual-phase load message");
    }

    @Test
    void attackLockCountsASpawnEntryThatBlocksEverySource() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));

        LockCategory<?> attack = LockCategories.byId("historystages:attacklock");
        assertTrue(attack.globalDualPhaseIds(stage).contains("minecraft:zombie"),
                "a spawn entry with no source filter implies an attack lock globally");
        assertTrue(attack.individualDualPhaseIds(stage).isEmpty(),
                "the individual side has never absorbed spawn locks");
    }

    /** Writes one plausible entry into the category. Returns how many were written. */
    @SuppressWarnings("unchecked")
    private static int writeSample(LockCategory<?> category, StageEntry stage) {
        switch (category.id()) {
            case "historystages:items", "historystages:mod_exceptions" ->
                    ((LockCategory<ItemEntry>) category)
                            .write(stage, List.of(new ItemEntry("minecraft:stone")));
            case "historystages:fluids" ->
                    ((LockCategory<net.bananemdnsa.historystages.data.FluidEntry>) category)
                            .write(stage, List.of(
                                    new net.bananemdnsa.historystages.data.FluidEntry("minecraft:lava")));
            case "historystages:tags", "historystages:mods" ->
                    ((LockCategory<NamedLockEntry>) category)
                            .write(stage, List.of(new NamedLockEntry("minecraft:logs")));
            case "historystages:recipes", "historystages:dimensions",
                 "historystages:structures", "historystages:biomes",
                 "historystages:attacklock" ->
                    ((LockCategory<String>) category).write(stage, List.of("minecraft:sample"));
            case "historystages:spawnlock" ->
                    ((LockCategory<EntitySpawnLockEntry>) category)
                            .write(stage, List.of(new EntitySpawnLockEntry("minecraft:zombie")));
            case "historystages:interactionlock" ->
                    ((LockCategory<EntityInteractionLockEntry>) category)
                            .write(stage, List.of(new EntityInteractionLockEntry("minecraft:villager")));
            default -> throw new AssertionError("no sample defined for " + category.id());
        }
        return 1;
    }
}
