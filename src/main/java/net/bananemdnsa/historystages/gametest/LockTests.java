package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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

    @GameTest(template = "empty")
    public static void anNbtCriterionIsNotAnsweredFromAnotherStack(GameTestHelper helper) {
        // The memo behind the item check remembers what gates an item, keyed by its id. That is
        // only legal while the answer cannot differ between two stacks of the same item — and an
        // NBT criterion is exactly what makes it differ. Get this wrong and one tagged sword's
        // verdict is served for every plain one, which reads as a config mistake, not a cache.
        try {
            JsonObject criterion = new JsonObject();
            criterion.addProperty("questItem", true);

            GameTestStages.global("nbt_lock", stage -> stage.setItemEntries(
                    new ArrayList<>(List.of(new ItemEntry(LOCKED_ITEM, criterion)))));

            ServerPlayer player = GameTestPlayers.create(helper);

            ItemStack tagged = new ItemStack(Items.DIAMOND_SWORD);
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("questItem", true);
            tagged.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);

            // Ask about the plain one first, so a memo would be filled with "not locked" before
            // the tagged one is ever seen. Asking the other way round would hide the fault.
            if (StageLockHelper.isItemLockedForPlayer(plain, player.getUUID())) {
                helper.fail("the plain sword matches no NBT criterion and must stay free");
                return;
            }
            if (!StageLockHelper.isItemLockedForPlayer(tagged, player.getUUID())) {
                helper.fail("the tagged sword matches the stage's NBT criterion and must be "
                        + "locked — it was answered from the plain sword's result");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void unlockingAStageIsSeenImmediatelyByTheMaskPath(GameTestHelper helper) {
        // The item answer is remembered; the player's unlocked set is a mask rebuilt from a
        // version counter. This is the pair working together: what gates the item does not
        // change, what the player holds does, and the check has to notice within the same tick.
        StageData data = StageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "mask_unlock";
        try {
            lockingStage("mask_unlock");
            ServerPlayer player = GameTestPlayers.create(helper);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

            if (!StageLockHelper.isItemLockedForPlayer(sword, player.getUUID())) {
                helper.fail(LOCKED_ITEM + " should start out locked");
                return;
            }

            data.addStage(stageId);

            if (StageLockHelper.isItemLockedForPlayer(sword, player.getUUID())) {
                helper.fail("the stage was just unlocked, but the check still reports the item "
                        + "as locked — a cached player mask outlived its version counter");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void unlockingAnIndividualStageIsSeenImmediately(GameTestHelper helper) {
        // The individual counterpart, and it needed writing: breaking the global mask's version
        // check failed two tests, breaking the individual one failed none. The per-player mask
        // is the half that goes stale per player, which is also the half nobody would notice.
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "individual_mask";
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.individual("individual_mask", stage ->
                    stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry(LOCKED_ITEM)))));

            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

            if (!StageLockHelper.isItemLockedForPlayer(sword, player.getUUID())) {
                helper.fail(LOCKED_ITEM + " sits in an individual stage this player has not "
                        + "unlocked and should start out locked");
                return;
            }

            data.addStage(player.getUUID(), stageId);

            if (StageLockHelper.isItemLockedForPlayer(sword, player.getUUID())) {
                helper.fail("the individual stage was just unlocked for this player, but the "
                        + "check still reports the item as locked — a cached player mask "
                        + "outlived its version counter");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(player.getUUID(), stageId);
        }
    }

    // --- Action locks ---
    //
    // isItemActionLocked is a different question from isItemLocked, and until now nothing
    // watched it: it decides whether a recipe may be crafted, whether loot may drop and whether
    // an icon is drawn, and it runs on the furnace path once per tick. Both directions appear,
    // because an implementation that always says "blocked" passes the first case alone and one
    // that always says "free" passes the second.

    @GameTest(template = "empty")
    public static void anActionListedOnTheEntryIsBlocked(GameTestHelper helper) {
        try {
            stageLockingActions("action_blocked", List.of("recipe"));

            if (!StageLockHelper.isActionLockedForServer(new ItemStack(Items.DIAMOND_SWORD), "recipe")) {
                helper.fail(LOCKED_ITEM + " lists \"recipe\" among its locked actions in a stage "
                        + "that is not unlocked, but the engine reports the action as allowed");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void anActionTheEntryDoesNotListIsFree(GameTestHelper helper) {
        try {
            stageLockingActions("action_allowed", List.of("recipe"));

            if (StageLockHelper.isActionLockedForServer(new ItemStack(Items.DIAMOND_SWORD), "loot")) {
                helper.fail(LOCKED_ITEM + " locks only \"recipe\", but \"loot\" was reported as "
                        + "blocked - the entry's action list is being ignored");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void anItemNoStageMentionsHasNoBlockedAction(GameTestHelper helper) {
        try {
            stageLockingActions("action_other_item", List.of("recipe"));

            // The path that made this worth guarding: an item no stage has ever heard of. The
            // candidate list is empty, so the answer is settled before anything is built to ask
            // the question with - and it must still be "free", not "free by accident".
            if (StageLockHelper.isActionLockedForServer(new ItemStack(Items.STONE), "recipe")) {
                helper.fail("minecraft:stone appears in no stage, but an action on it was "
                        + "reported as blocked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    // --- Mod entries, narrowed (Issue #117) ---
    //
    // The item tests above cover the item tab. A mod entry is the one a pack reaches for to gate
    // a whole namespace at once, and it is where the narrowing was reported broken: an
    // individual stage locking "create" for nothing but recipes froze everything else the mod
    // owned. Both the narrowed list and the empty one are asked about, because they used to be
    // written to disk as the same file.

    @GameTest(template = "empty")
    public static void aModEntryNarrowedToOneActionLeavesTheRestFree(GameTestHelper helper) {
        try {
            individualStageLockingMod("mod_narrowed", List.of("recipe"));
            ServerPlayer player = GameTestPlayers.create(helper);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

            if (!StageLockHelper.isActionLockedByIndividualStage(sword, player.getUUID(), "recipe")) {
                helper.fail("the mod entry lists \"recipe\", but the engine reports it as allowed");
                return;
            }
            for (String free : List.of("pickup", "use", "equip", "break")) {
                if (StageLockHelper.isActionLockedByIndividualStage(sword, player.getUUID(), free)) {
                    helper.fail("the mod entry locks only \"recipe\", but \"" + free + "\" was "
                            + "reported as blocked - the entry's action list is being ignored");
                    return;
                }
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aModEntryWithEveryActionClearedBlocksNothing(GameTestHelper helper) {
        try {
            individualStageLockingMod("mod_cleared", List.of());
            ServerPlayer player = GameTestPlayers.create(helper);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

            for (String action : net.bananemdnsa.historystages.api.lock.LockActions.ITEM) {
                if (StageLockHelper.isActionLockedByIndividualStage(sword, player.getUUID(), action)) {
                    helper.fail("every action was cleared on the mod entry, but \"" + action
                            + "\" was reported as blocked");
                    return;
                }
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** An individual stage locking the whole {@code minecraft} namespace, for the named actions. */
    private static void individualStageLockingMod(String name, List<String> actions) {
        GameTestStages.individual(name, stage -> stage.setModEntries(new ArrayList<>(
                List.of(new NamedLockEntry("minecraft", new ArrayList<>(actions))))));
    }

    /** A stage locking {@link #LOCKED_ITEM} for only the named actions. */
    private static void stageLockingActions(String name, List<String> actions) {
        GameTestStages.global(name, stage -> stage.setItemEntries(new ArrayList<>(
                List.of(new ItemEntry(LOCKED_ITEM, null, new ArrayList<>(actions))))));
    }

    /** A stage that locks {@link #LOCKED_ITEM}, written the way the editor tab writes it. */
    private static void lockingStage(String name) {
        GameTestStages.global(name, stage ->
                stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry(LOCKED_ITEM)))));
    }
}
