package net.bananemdnsa.historystages.gametest;

import java.util.UUID;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Stage state written and read back, through the real SavedData machinery.
 *
 * <p>Half of persistence. The other half is a restart, and a GameTest lives in one session — so
 * what this catches is a broken write or a broken read, not a stage that fails to survive a stop
 * and a start. That one is a human's job and the design says so, rather than leaving it to look
 * covered.
 *
 * <p>The individual half is tested beside the global one on purpose. Forgetting individual stages
 * is this project's most repeated mistake: feature after feature has handled the global map and
 * left the per-player one behind, and each time it was found in game rather than here.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersistenceTests {

    private PersistenceTests() {}

    @GameTest(template = "empty")
    public static void aGlobalStageIsReadBackAsUnlocked(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "persisted";
        try {
            if (data.hasStage(id)) {
                helper.fail("the test stage was already unlocked before this test ran, "
                        + "so an earlier test leaked it");
                return;
            }

            data.addStage(id);

            if (!data.hasStage(id)) {
                helper.fail("the stage was added and hasStage still reports it as locked");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(id);
        }
    }

    @GameTest(template = "empty")
    public static void aRemovedGlobalStageIsReadBackAsLocked(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "removed";
        try {
            data.addStage(id);
            data.removeStage(id);

            if (data.hasStage(id)) {
                helper.fail("the stage was removed and hasStage still reports it as unlocked");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(id);
        }
    }

    @GameTest(template = "empty")
    public static void anIndividualStageIsReadBackForThatPlayerOnly(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "individual";
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            data.addStage(first, id);

            if (!data.hasStage(first, id)) {
                helper.fail("the stage was unlocked for the first player "
                        + "and hasStage still reports it as locked for them");
                return;
            }
            // The half that gets forgotten: unlocking for one player must not unlock for everyone.
            if (data.hasStage(second, id)) {
                helper.fail("the stage was unlocked for one player "
                        + "and is reported as unlocked for a different one");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(first, id);
            data.removeStage(second, id);
        }
    }

    @GameTest(template = "empty")
    public static void aRemovedIndividualStageIsReadBackAsLocked(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "individual_removed";
        UUID player = UUID.randomUUID();
        try {
            data.addStage(player, id);
            data.removeStage(player, id);

            if (data.hasStage(player, id)) {
                helper.fail("the stage was removed for the player "
                        + "and hasStage still reports it as unlocked");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(player, id);
        }
    }

    @GameTest(template = "empty")
    public static void theGlobalAndIndividualStoresDoNotSeeEachOther(GameTestHelper helper) {
        StageData global = StageData.get(helper.getLevel());
        IndividualStageData individual = IndividualStageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "crosstalk";
        UUID player = UUID.randomUUID();
        try {
            global.addStage(id);

            // Two stores under the same id. A shared backing map would make this pass by accident
            // and hand every player a stage the pack meant to grant one at a time.
            if (individual.hasStage(player, id)) {
                helper.fail("a globally unlocked stage is reported as unlocked "
                        + "for an individual player who never received it");
                return;
            }
            helper.succeed();
        } finally {
            global.removeStage(id);
            individual.removeStage(player, id);
        }
    }
}
