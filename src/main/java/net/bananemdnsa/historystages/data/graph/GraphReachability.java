package net.bananemdnsa.historystages.data.graph;

import net.bananemdnsa.historystages.data.StageManager;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The single definition of what "reachable" means in the stage graph.
 *
 * <p>Two places need it and must agree: the node colouring and the {@code PROGRESSIVE}
 * visibility filter. They used to carry separate copies, which drifted — the filter let a
 * stage through that the colouring then drew as {@code LOCKED}.</p>
 */
public final class GraphReachability {

    private GraphReachability() {
    }

    /**
     * UNLOCKED when the viewer already has the stage; REACHABLE when they do not but its stage
     * prerequisites are satisfied; LOCKED otherwise.
     *
     * <p>Groups are AND-connected with each other, entries inside a group follow the group's
     * AND/OR flag. Evaluating a flattened prerequisite set instead would report a stage with a
     * satisfied OR group as locked.</p>
     *
     * <p>Item/XP/advancement requirements never enter into this: the client cannot evaluate all
     * of them, so "you still need the iron" must not make a stage read as unreachable. A group
     * that references no stages at all is therefore satisfied, and in an OR group they act as an
     * escape hatch around a locked stage reference.</p>
     *
     * @param key      namespaced graph key, see {@link StageManager#graphKey(String, boolean)}
     * @param prereqs  dependency groups from {@link StageManager#graphDependencyGroups()}
     * @param unlocked answers whether a namespaced key is unlocked for the viewer
     */
    public static NodeState resolve(String key,
                                    Map<String, List<StageManager.StageDepGroup>> prereqs,
                                    Predicate<String> unlocked) {
        if (unlocked.test(key)) return NodeState.UNLOCKED;
        for (StageManager.StageDepGroup group : prereqs.getOrDefault(key, List.of())) {
            if (!groupSatisfied(group, unlocked)) return NodeState.LOCKED;
        }
        return NodeState.REACHABLE;
    }

    /** True when the stage is unlocked or researchable right now, i.e. anything but LOCKED. */
    public static boolean isOpen(String key,
                                 Map<String, List<StageManager.StageDepGroup>> prereqs,
                                 Predicate<String> unlocked) {
        return resolve(key, prereqs, unlocked) != NodeState.LOCKED;
    }

    private static boolean groupSatisfied(StageManager.StageDepGroup group, Predicate<String> unlocked) {
        if (group.stageKeys().isEmpty()) return true;
        if (group.or()) {
            if (group.hasOtherRequirements()) return true;
            for (String depKey : group.stageKeys()) {
                if (unlocked.test(depKey)) return true;
            }
            return false;
        }
        for (String depKey : group.stageKeys()) {
            if (!unlocked.test(depKey)) return false;
        }
        return true;
    }
}
