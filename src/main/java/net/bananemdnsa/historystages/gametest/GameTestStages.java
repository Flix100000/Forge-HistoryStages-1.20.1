package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;

/**
 * Test stages, built in code and removed again.
 *
 * <p>No GameTest in this package reads a stage file. A test that needed one would need somebody to
 * have opened the editor first, and the suite would then depend on a hand-made file nobody
 * remembers creating. {@code StageManager} hands out its live maps, so a stage can simply be put in
 * and taken out.
 *
 * <p>Every id starts with {@code gametest:}, which is what lets {@link #removeAll()} find them
 * without keeping a register, and what makes a leaked one obvious in a stage list.
 */
final class GameTestStages {

    static final String PREFIX = "gametest:";

    private GameTestStages() {}

    /** A global stage with the given dependency groups, registered under {@code gametest:<name>}. */
    static StageEntry global(String name, DependencyGroup... groups) {
        return global(name, entry -> {}, groups);
    }

    /**
     * A global stage filled in by {@code fill} before it is published.
     *
     * <p>Use this rather than mutating the returned entry afterwards whenever the stage carries
     * locks. The relevance index in front of the item scans is rebuilt from the stage maps on the
     * next query after a change, and creating the test player is itself such a query: an entry
     * published empty and filled in afterwards gets indexed empty, and the index is then clean
     * and wrong. It does not throw — it simply reports the staged item as unlocked.
     */
    static StageEntry global(String name, Consumer<StageEntry> fill, DependencyGroup... groups) {
        StageEntry entry = newEntry(name, groups);
        fill.accept(entry);
        StageManager.getStages().put(PREFIX + name, entry);
        StageLocks.stagesChanged();
        return entry;
    }

    /** The same, in the individual map. */
    static StageEntry individual(String name, DependencyGroup... groups) {
        return individual(name, entry -> {}, groups);
    }

    /** The individual counterpart of {@link #global(String, Consumer, DependencyGroup...)}. */
    static StageEntry individual(String name, Consumer<StageEntry> fill, DependencyGroup... groups) {
        StageEntry entry = newEntry(name, groups);
        fill.accept(entry);
        StageManager.getIndividualStages().put(PREFIX + name, entry);
        StageLocks.stagesChanged();
        return entry;
    }

    private static StageEntry newEntry(String name, DependencyGroup... groups) {
        StageEntry entry = new StageEntry();
        entry.setDisplayName(name);
        entry.setDependencies(new ArrayList<>(List.of(groups)));
        return entry;
    }

    /**
     * Removes every stage this class created.
     *
     * <p>Call from a {@code finally}, never from the end of a test body. A test that fails partway
     * leaves its stages behind, the next test picks them up, and the failure is then reported
     * against a test that did nothing wrong.
     */
    static void removeAll() {
        StageManager.getStages().keySet().removeIf(id -> id.startsWith(PREFIX));
        StageManager.getIndividualStages().keySet().removeIf(id -> id.startsWith(PREFIX));
        StageLocks.stagesChanged();
    }
}
