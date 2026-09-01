package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.FluidEntry;
import net.bananemdnsa.historystages.data.lock.engine.FluidContent;
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

    /** A stage that gates one fluid and names no item at all — the narrowing's hardest case. */
    private static void stageGating(String name, String fluidId) {
        GameTestStages.global(name, stage -> stage.setFluidEntries(
                new ArrayList<>(List.of(new FluidEntry(fluidId)))));
    }
}
