package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
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
 * <p>Asked through {@code StageLockHelper}, which is the path the mod's own handlers use.
 * <strong>Not</strong> {@code CategoryLocks.isLockedForPlayer}: that one resolves through
 * {@code LockCategory.matches}, which the built-in categories deliberately leave at its default of
 * false — they are queried through their own typed paths instead. It therefore answers "not locked"
 * for every built-in category, silently, and the first version of this test believed it. Worth
 * knowing before Phase 9 freezes that method as public API.
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

    /** A stage that locks {@link #LOCKED_ITEM}, written the way the editor tab writes it. */
    private static void lockingStage(String name) {
        StageEntry stage = GameTestStages.global(name);
        stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry(LOCKED_ITEM))));
    }
}
