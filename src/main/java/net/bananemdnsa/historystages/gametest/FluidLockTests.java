package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.FluidEntry;
import net.bananemdnsa.historystages.data.lock.FluidRecipeIndex;
import net.bananemdnsa.historystages.data.lock.engine.FluidContent;
import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fluid seam, answered against a live registry.
 *
 * <p>The whole claim of the fluid category is that a bucket <em>nobody listed</em> comes back
 * locked, because the stage names the fluid and the capability names the container. Nothing about
 * that can be shown without a real {@code ItemStack}: the unit tests cannot build one, and the
 * capability they would have to ask does not exist outside a running game.
 *
 * <p>Asked through {@code StageLockHelper}, the same path the mod's own handlers take.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidLockTests {

    private static final String LOCKED_FLUID = "minecraft:lava";

    private FluidLockTests() {}

    /**
     * A stage that gates one fluid and names no item at all — the narrowing's hardest case.
     *
     * <p>Deliberately above the tests rather than below them. {@code GameTestCleanupGuardTest}
     * slices the file from one test to the next and gives the last test everything to the end,
     * so a stage-creating helper sitting after it gets blamed on that test.
     */
    private static void stageGating(String name, String fluidId) {
        GameTestStages.global(name, stage -> stage.setFluidEntries(
                new ArrayList<>(List.of(new FluidEntry(fluidId)))));
    }

    /** The claim itself: the stage names lava, and a bucket it never mentions is locked. */
    @GameTest(template = "empty")
    public static void aBucketOfAGatedFluidIsLockedWithoutBeingListed(GameTestHelper helper) {
        try {
            stageGating("fluid_bucket", LOCKED_FLUID);

            if (!StageLockHelper.isActionLockedForServer(new ItemStack(Items.LAVA_BUCKET), "use")) {
                helper.fail("a stage gates " + LOCKED_FLUID + " and is not unlocked, but a lava "
                        + "bucket reports \"use\" as allowed - the fluid capability is not "
                        + "reaching the lock engine");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** The other half: gating one fluid must not gate every container. */
    @GameTest(template = "empty")
    public static void aBucketOfAnUngatedFluidStaysFree(GameTestHelper helper) {
        try {
            stageGating("fluid_other", LOCKED_FLUID);

            if (StageLockHelper.isActionLockedForServer(new ItemStack(Items.WATER_BUCKET), "use")) {
                helper.fail("only " + LOCKED_FLUID + " is gated, but a water bucket was reported "
                        + "as blocked - the entry's fluid id is being ignored");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * An empty bucket carries nothing, so the container path cannot see it — which is precisely
     * why taking a fluid out of the world needed a handler of its own.
     */
    @GameTest(template = "empty")
    public static void anEmptyBucketCarriesNoFluid(GameTestHelper helper) {
        try {
            stageGating("fluid_empty", LOCKED_FLUID);

            if (FluidContent.of(new ItemStack(Items.BUCKET)) != null) {
                helper.fail("an empty bucket reported fluid contents");
                return;
            }
            if (StageLockHelper.isActionLockedForServer(new ItemStack(Items.BUCKET), "use")) {
                helper.fail("an empty bucket was reported as blocked by a fluid gate");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** The capability really names the fluid, rather than the lock happening to agree. */
    @GameTest(template = "empty")
    public static void aFilledBucketNamesItsFluid(GameTestHelper helper) {
        try {
            String found = FluidContent.of(new ItemStack(Items.LAVA_BUCKET));
            if (!LOCKED_FLUID.equals(found)) {
                helper.fail("a lava bucket reported its contents as '" + found + "'");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** A narrowed entry gates what it names and nothing else. */
    @GameTest(template = "empty")
    public static void anActionTheFluidEntryDoesNotListIsFree(GameTestHelper helper) {
        try {
            GameTestStages.global("fluid_actions", stage -> stage.setFluidEntries(new ArrayList<>(
                    List.of(new FluidEntry(LOCKED_FLUID, new ArrayList<>(List.of("use")),
                            null, null)))));

            ItemStack bucket = new ItemStack(Items.LAVA_BUCKET);
            if (!StageLockHelper.isActionLockedForServer(bucket, "use")) {
                helper.fail("the entry lists \"use\" but it was reported as allowed");
                return;
            }
            if (StageLockHelper.isActionLockedForServer(bucket, "loot")) {
                helper.fail("the entry lists only \"use\", but \"loot\" was reported as blocked - "
                        + "the fluid entry's action list is being ignored");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The bare-id form the pickup handler uses has to agree with the container form. It reaches
     * the same stages by a different route — no stack, no item id, only the fluid.
     */
    @GameTest(template = "empty")
    public static void theBareFluidIdFormAgreesWithTheContainerForm(GameTestHelper helper) {
        try {
            stageGating("fluid_bare", LOCKED_FLUID);

            if (!StageLockHelper.isFluidActionLockedForServer(LOCKED_FLUID, "pickup")) {
                helper.fail("a stage gates " + LOCKED_FLUID + ", but the bare-id query reports "
                        + "\"pickup\" as allowed - the pickup handler would let it through");
                return;
            }
            if (StageLockHelper.isFluidActionLockedForServer("minecraft:water", "pickup")) {
                helper.fail("the bare-id query reported an ungated fluid as blocked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** No fluid entry anywhere means the fluid gate answers no, whatever is being held. */
    @GameTest(template = "empty")
    public static void withNoFluidEntryNothingIsGated(GameTestHelper helper) {
        try {
            if (StageLockHelper.isActionLockedForServer(new ItemStack(Items.LAVA_BUCKET), "use")) {
                helper.fail("no stage gates any fluid, yet a lava bucket was reported as blocked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    // ---- the recipe side ------------------------------------------------------------

    /**
     * The claim round 1 shipped without a test: a recipe whose result is a filled bucket is
     * already gated, because the result is an item and the capability seam sees the fluid in it.
     * Asked at the level RecipeHandler.isOutputLocked asks it.
     */
    @GameTest(template = "empty")
    public static void aFilledBucketAsARecipeResultIsGated(GameTestHelper helper) {
        try {
            stageGating("fluid_recipe_result", LOCKED_FLUID);

            if (!StageLockHelper.isActionLockedForServer(
                    new ItemStack(Items.LAVA_BUCKET), "recipe")) {
                helper.fail("a stage gates " + LOCKED_FLUID + ", but a lava bucket as a recipe "
                        + "result reports \"recipe\" as allowed - a recipe producing one would "
                        + "stay craftable");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The re-encode has to survive a real recipe list.
     *
     * <p>Every recipe carries a codec, but a codec only has to <em>decode</em> to have got the
     * recipe loaded — encoding is the direction nobody exercises, and a serialiser that throws
     * there would leave the index quietly empty. An index that scanned nothing looks exactly like
     * an index that found nothing, which is why this counts rather than trusting.
     *
     * <p>The tolerance is loose on purpose: this guards "the route is broken", not "every mod
     * writes a symmetric codec". For the record, at the time of writing the real numbers on this
     * dev environment are 1299 recipes scanned and <strong>zero</strong> unreadable — so a run
     * reporting a handful is worth a look even though it passes, and one reporting hundreds means
     * the codec route has stopped working.
     */
    @GameTest(template = "empty")
    public static void everyLoadedRecipeCanBeTurnedBackIntoJson(GameTestHelper helper) {
        try {
            stageGating("fluid_recipe_scan", LOCKED_FLUID);

            FluidRecipeIndex.clear();
            FluidRecipeIndex.markDirty();
            FluidRecipeIndex.rebuildIfDirty(
                    helper.getLevel().getServer().getRecipeManager().getOrderedRecipes(),
                    helper.getLevel().registryAccess());

            int scanned = FluidRecipeIndex.lastScanned();
            int unreadable = FluidRecipeIndex.lastUnreadable();

            if (scanned < 100) {
                helper.fail("the fluid recipe index only looked at " + scanned + " recipes - "
                        + "a vanilla server has hundreds, so it was handed the wrong list");
                return;
            }
            if (unreadable > scanned / 10) {
                helper.fail(unreadable + " of " + scanned + " recipes could not be re-encoded; "
                        + "the codec route does not work and the index cannot see fluid results");
                return;
            }
            helper.succeed();
        } finally {
            FluidRecipeIndex.clear();
            GameTestStages.removeAll();
        }
    }

    /** No fluid gated anywhere means no index at all — a pack not using fluids pays nothing. */
    @GameTest(template = "empty")
    public static void withNoFluidGatedTheIndexIsNotEvenBuilt(GameTestHelper helper) {
        try {
            FluidRecipeIndex.clear();
            FluidRecipeIndex.markDirty();
            FluidRecipeIndex.rebuildIfDirty(
                    helper.getLevel().getServer().getRecipeManager().getOrderedRecipes(),
                    helper.getLevel().registryAccess());

            if (FluidRecipeIndex.lastScanned() != 0) {
                helper.fail("no stage gates a fluid, but the index still walked "
                        + FluidRecipeIndex.lastScanned() + " recipes");
                return;
            }
            if (!FluidRecipeIndex.isEmpty()) {
                helper.fail("no stage gates a fluid, but the index is not empty");
                return;
            }
            helper.succeed();
        } finally {
            FluidRecipeIndex.clear();
        }
    }

    /** With nothing indexed, the viewer question is a cheap no rather than a guess. */
    @GameTest(template = "empty")
    public static void anUnindexedRecipeIsNotGated(GameTestHelper helper) {
        try {
            FluidRecipeIndex.clear();
            if (RecipeHandler.isFluidGatedForViewer("minecraft:stick")) {
                helper.fail("an unindexed recipe was reported as gated by a fluid");
                return;
            }
            helper.succeed();
        } finally {
            FluidRecipeIndex.clear();
        }
    }
}
