package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The lock engine answering about a real item, for a real player.
 *
 * <p>One step out from {@code DependencyCheckerTests}: that asks whether a stage <em>may</em> be
 * unlocked, this asks what a locked stage actually does to an item. Both halves matter and a
 * mistake in either looks the same from a player's chair.
 *
 * <p>Asked through {@code StageLockHelper}, which is the path the mod's own handlers use. Until
 * Phase 8 that choice mattered a great deal: {@code CategoryLocks.isLockedForPlayer} resolved
 * through {@code LockCategory.matches}, which the built-in categories left at its default of
 * false, so it answered "not locked" for every one of them — silently, and the first version of
 * this test believed it. The built-ins now answer for themselves and both routes agree;
 * {@code CategoryLocksBuiltInTest} holds that.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LockTests {

    private static final String LOCKED_ITEM = "minecraft:diamond_sword";

    private LockTests() {}

    @GameTest(template = "empty")
    public static void anItemInALockedStageIsLocked(GameTestHelper helper) {
        try {
            lockingStage("locked_item");
            ServerPlayer player = GameTestPlayers.create(helper);

            if (!StageLockHelper.isItemLockedForPlayer(new ItemStack(Items.DIAMOND_SWORD),
                    player.getUUID())) {
                helper.fail(LOCKED_ITEM + " sits in a stage that is not unlocked, "
                        + "but the lock engine reports it as available");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void theSameItemIsFreeOnceTheStageIsUnlocked(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "locked_item";
        try {
            lockingStage("locked_item");
            data.addStage(stageId);

            ServerPlayer player = GameTestPlayers.create(helper);

            if (StageLockHelper.isItemLockedForPlayer(new ItemStack(Items.DIAMOND_SWORD),
                    player.getUUID())) {
                helper.fail("the stage holding " + LOCKED_ITEM + " is unlocked, "
                        + "but the lock engine still reports the item as locked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            // Unlocked state lives in SavedData and outlives both the test and the stage entry.
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void anItemNobodyLockedIsFree(GameTestHelper helper) {
        try {
            lockingStage("locked_item");
            ServerPlayer player = GameTestPlayers.create(helper);

            // The control. Without it an engine that answers "locked" to everything passes the
            // first test, and every item in the game would be unusable.
            if (StageLockHelper.isItemLockedForPlayer(new ItemStack(Items.DIRT),
                    player.getUUID())) {
                helper.fail("minecraft:dirt is in no stage at all, "
                        + "but the lock engine reports it as locked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void anItemIsLockedByItsModAndFreedByAnException(GameTestHelper helper) {
        try {
            GameTestStages.global("mod_lock", stage -> {
                stage.setMods(new ArrayList<>(List.of("minecraft")));
                stage.setModExceptions(new ArrayList<>(List.of("minecraft:stone")));
            });

            ServerPlayer player = GameTestPlayers.create(helper);

            if (!StageLockHelper.isItemLockedForPlayer(new ItemStack(Items.DIAMOND), player.getUUID())) {
                helper.fail("minecraft:diamond belongs to a locked mod, "
                        + "but the lock engine reports it as available");
                return;
            }
            if (StageLockHelper.isItemLockedForPlayer(new ItemStack(Items.STONE), player.getUUID())) {
                helper.fail("minecraft:stone is a mod exception on the locking stage, "
                        + "but the lock engine still reports it as locked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aStageGatingAnItemByBothIdAndModIsReportedOnce(GameTestHelper helper) {
        // Items, mods and tags are three categories but one question. Asking them separately
        // would name this stage twice and in a different order, and that order is what the
        // "you still need" tooltip prints.
        try {
            GameTestStages.global("id_and_mod", stage -> {
                stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry("minecraft:diamond"))));
                stage.setMods(new ArrayList<>(List.of("minecraft")));
            });

            List<String> gating = StageLocks.engine().gatingStagesForItem(
                    "minecraft:diamond", "minecraft", new ItemStack(Items.DIAMOND), StageScope.GLOBAL);

            if (!gating.equals(List.of(GameTestStages.PREFIX + "id_and_mod"))) {
                helper.fail("expected the gating stage exactly once, got " + gating);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void everyGatingStageIsNamedInCandidateOrder(GameTestHelper helper) {
        try {
            GameTestStages.global("by_id", stage ->
                    stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry("minecraft:diamond")))));
            GameTestStages.global("by_mod", stage ->
                    stage.setMods(new ArrayList<>(List.of("minecraft"))));

            List<String> gating = StageLocks.engine().gatingStagesForItem(
                    "minecraft:diamond", "minecraft", new ItemStack(Items.DIAMOND), StageScope.GLOBAL);

            // The relevance index lists id hits before mod hits, and that is the order the
            // caller gets — not the stage map's.
            if (!gating.equals(List.of(GameTestStages.PREFIX + "by_id", GameTestStages.PREFIX + "by_mod"))) {
                helper.fail("expected [by_id, by_mod] in candidate order, got " + gating);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void anIndividualStageLocksTheItemForThatPlayerOnly(GameTestHelper helper) {
        // Individual stages are the half that features keep forgetting; the item path has its
        // own copy of every rule, so it gets its own test.
        try {
            GameTestStages.individual("individual_item", stage ->
                    stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry(LOCKED_ITEM)))));

            ServerPlayer player = GameTestPlayers.create(helper);

            if (!StageLockHelper.isItemLockedForPlayer(new ItemStack(Items.DIAMOND_SWORD),
                    player.getUUID())) {
                helper.fail(LOCKED_ITEM + " sits in an individual stage this player has not "
                        + "unlocked, but the lock engine reports it as available");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** A stage that locks {@link #LOCKED_ITEM}, written the way the editor tab writes it. */
    private static void lockingStage(String name) {
        GameTestStages.global(name, stage ->
                stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry(LOCKED_ITEM)))));
    }
}
