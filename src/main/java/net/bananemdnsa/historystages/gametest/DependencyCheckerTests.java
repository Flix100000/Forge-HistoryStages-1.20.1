package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.DependencyProgress;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.data.dependency.StatDep;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What {@code DependencyChecker} answers, with a real player and a real level.
 *
 * <p>Ninety-three lines that decide whether a player may progress, and until this class the only
 * thing watching them was a text guard checking that nobody reads a group's fields directly. What
 * {@code checkAll} <em>answers</em> was verified by nothing. A wrong answer here does not clip a
 * button; it stops somebody playing.
 *
 * <p>Each requirement kind appears twice, once met and once not. Either alone proves little: a
 * checker that always says yes passes every met case, and one that always says no passes every
 * unmet case.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DependencyCheckerTests {

    private DependencyCheckerTests() {}

    // --- Items ---

    @GameTest(template = "empty")
    public static void itemRequirementIsUnmetWithNothingDeposited(GameTestHelper helper) {
        try {
            StageEntry stage = GameTestStages.global("items", itemGroup());
            ServerPlayer player = GameTestPlayers.create(helper);

            if (check(stage, player, StageScope.GLOBAL, null).isFulfilled()) {
                helper.fail("three diamonds are required and nothing has been deposited, "
                        + "but the checker reported the stage as fulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void itemRequirementIsMetOnceEnoughIsDeposited(GameTestHelper helper) {
        try {
            StageEntry stage = GameTestStages.global("items", itemGroup());
            ServerPlayer player = GameTestPlayers.create(helper);

            // Items are handed in, not carried: the requirement reads the deposited tag and never
            // looks at the inventory. Writing this test against the inventory was the first thing
            // these tests caught, and it was a wrong idea about the mod rather than a bug in it.
            CompoundTag deposited = new CompoundTag();
            deposited.putInt("Group_0_Item_minecraft:diamond", 3);

            if (!check(stage, player, StageScope.GLOBAL, deposited).isFulfilled()) {
                helper.fail("three diamonds are deposited and three are required, "
                        + "but the checker reported the stage as unfulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void itemRequirementIsUnmetWhenTooLittleIsDeposited(GameTestHelper helper) {
        try {
            StageEntry stage = GameTestStages.global("items", itemGroup());
            ServerPlayer player = GameTestPlayers.create(helper);

            // One short. Without this case a checker using > instead of >= passes both of the
            // above, and the player who deposited everything would be told to deposit more.
            CompoundTag deposited = new CompoundTag();
            deposited.putInt("Group_0_Item_minecraft:diamond", 2);

            if (check(stage, player, StageScope.GLOBAL, deposited).isFulfilled()) {
                helper.fail("two of three diamonds are deposited, "
                        + "but the checker reported the stage as fulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void depositedItemsStayWithTheirGroupWhenAnEarlierGroupIsDeleted(GameTestHelper helper) {
        try {
            DependencyGroup first = new DependencyGroup();
            first.setItems(new ArrayList<>(List.of(new DependencyItem("minecraft:emerald", 1))));
            DependencyGroup second = itemGroup();
            StageEntry stage = GameTestStages.global("group_identity", first, second);
            // What loading a stage file does, and the only reason the group below can be told
            // apart from the one that is about to take its place.
            DependencyProgress.assignIds(stage.getDependencies());
            ServerPlayer player = GameTestPlayers.create(helper);

            CompoundTag deposited = new CompoundTag();
            deposited.putInt(DependencyProgress.key(DependencyProgress.groupKey(second, 1),
                    DependencyProgress.itemSuffix("minecraft:diamond")), 3);

            // The editor's "remove group" on the first one. Groups are AND-connected, so what is
            // left is exactly the diamond requirement the player has already paid.
            stage.getDependencies().remove(0);

            if (!check(stage, player, StageScope.GLOBAL, deposited).isFulfilled()) {
                helper.fail("three diamonds were deposited into the second group and the first"
                        + " group was then deleted, but the checker no longer counts them");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    private static DependencyGroup itemGroup() {
        DependencyGroup group = new DependencyGroup();
        group.setItems(new ArrayList<>(List.of(new DependencyItem("minecraft:diamond", 3))));
        return group;
    }

    // --- XP ---

    @GameTest(template = "empty")
    public static void xpRequirementIsUnmetAtLevelZero(GameTestHelper helper) {
        try {
            StageEntry stage = GameTestStages.global("xp", xpGroup());
            ServerPlayer player = GameTestPlayers.create(helper);

            if (check(stage, player, StageScope.INDIVIDUAL, null).isFulfilled()) {
                helper.fail("level 5 is required and the player is at level 0, "
                        + "but the checker reported the stage as fulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void xpRequirementIsMetAtTheRequiredLevel(GameTestHelper helper) {
        try {
            StageEntry stage = GameTestStages.global("xp", xpGroup());
            ServerPlayer player = GameTestPlayers.create(helper);
            player.giveExperienceLevels(5);

            if (!check(stage, player, StageScope.INDIVIDUAL, null).isFulfilled()) {
                helper.fail("the player is at level " + player.experienceLevel
                        + " and level 5 is required, "
                        + "but the checker reported the stage as unfulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    private static DependencyGroup xpGroup() {
        DependencyGroup group = new DependencyGroup();
        group.setXpLevel(new XpLevelDep(5, false));
        return group;
    }

    // --- Stats ---

    @GameTest(template = "empty")
    public static void statRequirementIsUnmetAtZero(GameTestHelper helper) {
        try {
            StageEntry stage = GameTestStages.global("stat", statGroup());
            ServerPlayer player = GameTestPlayers.create(helper);

            if (check(stage, player, StageScope.INDIVIDUAL, null).isFulfilled()) {
                helper.fail("five jumps are required and the player has jumped zero times, "
                        + "but the checker reported the stage as fulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void statRequirementIsMetOnceTheStatIsHighEnough(GameTestHelper helper) {
        try {
            StageEntry stage = GameTestStages.global("stat", statGroup());
            ServerPlayer player = GameTestPlayers.create(helper);
            player.awardStat(Stats.CUSTOM.get(Stats.JUMP), 5);

            if (!check(stage, player, StageScope.INDIVIDUAL, null).isFulfilled()) {
                helper.fail("the jump stat is "
                        + player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP))
                        + " and 5 is required, "
                        + "but the checker reported the stage as unfulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    private static DependencyGroup statGroup() {
        DependencyGroup group = new DependencyGroup();
        group.setStats(new ArrayList<>(List.of(new StatDep("minecraft:jump", 5))));
        return group;
    }

    // --- Another stage as a prerequisite ---

    @GameTest(template = "empty")
    public static void stageRequirementIsUnmetWhileThePrerequisiteIsLocked(GameTestHelper helper) {
        try {
            GameTestStages.global("prerequisite");
            StageEntry stage = GameTestStages.global("dependent", stageGroup());
            ServerPlayer player = GameTestPlayers.create(helper);

            if (check(stage, player, StageScope.GLOBAL, null).isFulfilled()) {
                helper.fail("the prerequisite stage is not unlocked, "
                        + "but the checker reported the dependent stage as fulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void stageRequirementIsMetOnceThePrerequisiteIsUnlocked(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String prerequisite = GameTestStages.PREFIX + "prerequisite";
        try {
            GameTestStages.global("prerequisite");
            data.addStage(prerequisite);

            StageEntry stage = GameTestStages.global("dependent", stageGroup());
            ServerPlayer player = GameTestPlayers.create(helper);

            if (!check(stage, player, StageScope.GLOBAL, null).isFulfilled()) {
                helper.fail("the prerequisite stage is unlocked, "
                        + "but the checker reported the dependent stage as unfulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            // Unlocked state lives in SavedData and outlives both the test and the stage entry.
            data.removeStage(prerequisite);
        }
    }

    private static DependencyGroup stageGroup() {
        DependencyGroup group = new DependencyGroup();
        group.setStages(new ArrayList<>(List.of(GameTestStages.PREFIX + "prerequisite")));
        return group;
    }

    // --- An individual stage as a prerequisite, demanded of the researcher alone ---

    @GameTest(template = "empty")
    public static void playerModeIsUnmetWhileTheResearcherLacksTheStage(GameTestHelper helper) {
        try {
            GameTestStages.individual("prerequisite");
            StageEntry stage = GameTestStages.individual("dependent", playerModeGroup());
            ServerPlayer player = GameTestPlayers.create(helper);

            if (check(stage, player, StageScope.INDIVIDUAL, null).isFulfilled()) {
                helper.fail("the researcher does not have the prerequisite individual stage, "
                        + "but the checker reported the dependent stage as fulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The case the mode exists for, and the one that catches it being ignored.
     *
     * <p>Nobody is online in a test — the player below is built directly and never joins — so
     * {@code all_online} and {@code all_ever} both answer no here whatever this player holds. A
     * {@code player} mode that fell through to either of them would leave this test failing, which
     * is what makes it worth writing.
     */
    @GameTest(template = "empty")
    public static void playerModeIsMetOnceTheResearcherHasTheStage(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer player = GameTestPlayers.create(helper);
        String prerequisite = GameTestStages.PREFIX + "prerequisite";
        try {
            GameTestStages.individual("prerequisite");
            data.addStage(player.getUUID(), prerequisite);

            StageEntry stage = GameTestStages.individual("dependent", playerModeGroup());

            if (!check(stage, player, StageScope.INDIVIDUAL, null).isFulfilled()) {
                helper.fail("the researcher has the prerequisite individual stage, "
                        + "but the checker reported the dependent stage as unfulfilled");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            // Unlocked state lives in SavedData and outlives both the test and the stage entry.
            data.removeStage(player.getUUID(), prerequisite);
        }
    }

    private static DependencyGroup playerModeGroup() {
        DependencyGroup group = new DependencyGroup();
        group.setIndividualStages(new ArrayList<>(List.of(new IndividualStageDep(
                GameTestStages.PREFIX + "prerequisite", IndividualStageDep.MODE_PLAYER))));
        return group;
    }

    // --- Shared ---

    /**
     * Runs the checker.
     *
     * <p>The scope is an argument and not a constant, because it decides which requirements are
     * asked at all: {@code checkGroup} iterates {@code RequirementTypes.forScope(scope)}, and a
     * requirement the scope cannot answer is skipped rather than failed. XP and stats are
     * {@code INDIVIDUAL}-only, so asking them of a global stage leaves the group empty — and an
     * empty group counts as fulfilled. Passing the wrong scope therefore produces a test that
     * passes while checking nothing, which is worse than one that fails.
     */
    private static RequirementResult check(StageEntry stage, ServerPlayer player, StageScope scope,
            CompoundTag deposited) {
        return DependencyChecker.checkAll(stage, player, player.level(), scope, deposited);
    }
}
