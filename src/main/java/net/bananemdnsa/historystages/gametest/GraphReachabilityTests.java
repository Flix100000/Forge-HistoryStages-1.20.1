package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.graph.GraphReachability;
import net.bananemdnsa.historystages.data.graph.NodeState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the stage graph is allowed to conclude from an individual-stage prerequisite.
 *
 * <p>The graph runs on the client, and a client knows exactly one thing about individual stages:
 * which ones it holds itself. That is the right answer for a {@code player} prerequisite and no
 * answer at all for one demanded of everyone online or of everyone ever seen — yet the graph
 * asked the same question for all three and painted the node green off the viewer's own copy.
 *
 * <p>These tests sit on {@code GraphReachability} and on the key sets it reads rather than on the
 * canvas: the drawing is client-only and a dedicated test server cannot load it, while the
 * decision is common code and is where the mistake actually lived.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GraphReachabilityTests {

    private GraphReachabilityTests() {}

    private static final String PREREQUISITE = GameTestStages.PREFIX + "prerequisite";
    private static final String DEPENDENT = GameTestStages.PREFIX + "dependent";

    @GameTest(template = "empty")
    public static void aPlayerPrerequisiteLocksTheStageUntilTheViewerHasIt(GameTestHelper helper) {
        try {
            GameTestStages.individual("prerequisite");
            GameTestStages.individual("dependent", groupWith(IndividualStageDep.MODE_PLAYER));

            NodeState state = resolveDependent(Set.of());

            if (state != NodeState.LOCKED) {
                helper.fail("the viewer does not have the player-mode prerequisite, "
                        + "so the dependent stage must read as LOCKED, but it read as " + state);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aPlayerPrerequisiteOpensTheStageOnceTheViewerHasIt(GameTestHelper helper) {
        try {
            GameTestStages.individual("prerequisite");
            GameTestStages.individual("dependent", groupWith(IndividualStageDep.MODE_PLAYER));

            NodeState state = resolveDependent(Set.of(StageManager.graphKey(PREREQUISITE, true)));

            if (state != NodeState.REACHABLE) {
                helper.fail("the viewer has the player-mode prerequisite, so the dependent stage "
                        + "must read as REACHABLE, but it read as " + state);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The case the split exists for.
     *
     * <p>Nobody asked the viewer anything: the requirement is about the whole server. Reporting
     * LOCKED would be a verdict on a question this side never put, so the stage stays open the
     * way one waiting on undeposited items does.
     */
    @GameTest(template = "empty")
    public static void aPrerequisiteDemandedOfEveryoneIsNotDecidedFromHere(GameTestHelper helper) {
        try {
            GameTestStages.individual("prerequisite");
            GameTestStages.individual("dependent", groupWith(IndividualStageDep.MODE_ALL_ONLINE));

            NodeState state = resolveDependent(Set.of());

            if (state != NodeState.REACHABLE) {
                helper.fail("an all_online prerequisite cannot be evaluated by a viewer, so the "
                        + "dependent stage must not read as LOCKED, but it read as " + state);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The line survives the split.
     *
     * <p>Dropping the reference from {@code stageKeys} would have been the easy way to stop the
     * graph deciding on it, and it would have taken the edge with it — the dependency is real and
     * has to stay visible whatever the viewer can work out about it.
     */
    @GameTest(template = "empty")
    public static void aPrerequisiteDemandedOfEveryoneStillDrawsItsEdge(GameTestHelper helper) {
        try {
            GameTestStages.individual("prerequisite");
            GameTestStages.individual("dependent", groupWith(IndividualStageDep.MODE_ALL_ONLINE));

            String prerequisiteKey = StageManager.graphKey(PREREQUISITE, true);
            StageManager.StageDepGroup group = dependentGroup();

            if (!group.stageKeys().contains(prerequisiteKey)) {
                helper.fail("the prerequisite is missing from stageKeys, so no edge would be "
                        + "drawn for it");
                return;
            }
            if (group.checkableKeys().contains(prerequisiteKey)) {
                helper.fail("an all_online prerequisite is in checkableKeys, so reachability and "
                        + "the edge colour would both claim to know something they cannot");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    // --- Shared ---

    private static DependencyGroup groupWith(String mode) {
        DependencyGroup group = new DependencyGroup();
        group.setIndividualStages(new ArrayList<>(
                List.of(new IndividualStageDep(PREREQUISITE, mode))));
        return group;
    }

    /** The one group the dependent stage carries, as the graph reduces it. */
    private static StageManager.StageDepGroup dependentGroup() {
        List<StageManager.StageDepGroup> groups = StageManager.graphDependencyGroups()
                .get(StageManager.graphKey(DEPENDENT, true));
        return groups.get(0);
    }

    /** What the graph would make of the dependent stage for a viewer holding {@code unlocked}. */
    private static NodeState resolveDependent(Set<String> unlocked) {
        return GraphReachability.resolve(StageManager.graphKey(DEPENDENT, true),
                StageManager.graphDependencyGroups(), unlocked::contains);
    }
}
