package net.bananemdnsa.historystages.data.dependency;

import java.util.ArrayList;
import java.util.List;

public class DependencyResult {
    private final boolean fulfilled;
    private final List<GroupResult> groups;

    public DependencyResult(boolean fulfilled, List<GroupResult> groups) {
        this.fulfilled = fulfilled;
        this.groups = groups;
    }

    public boolean isFulfilled() { return fulfilled; }
    public List<GroupResult> getGroups() { return groups; }

    /**
     * Result with no dependencies — always fulfilled.
     */
    public static DependencyResult noDependencies() {
        return new DependencyResult(true, new ArrayList<>());
    }

    public static class GroupResult {
        private final String logic;
        private final boolean fulfilled;
        private final List<EntryResult> entries;

        public GroupResult(String logic, boolean fulfilled, List<EntryResult> entries) {
            this.logic = logic;
            this.fulfilled = fulfilled;
            this.entries = entries;
        }

        public String getLogic() { return logic; }
        public boolean isFulfilled() { return fulfilled; }
        public List<EntryResult> getEntries() { return entries; }
    }

    public static class EntryResult {
        private final String type;       // "item", "stage", "individual_stage", "advancement", "xp_level", "entity_kill", "stat"
        private final String description; // Human-readable, e.g. "10x Iron Ingot"
        private final boolean fulfilled;
        private final int current;        // Current progress (e.g. current kill count)
        private final int required;       // Required amount

        public EntryResult(String type, String description, boolean fulfilled, int current, int required) {
            this.type = type;
            this.description = description;
            this.fulfilled = fulfilled;
            this.current = current;
            this.required = required;
        }

        public EntryResult(String type, String description, boolean fulfilled) {
            this(type, description, fulfilled, fulfilled ? 1 : 0, 1);
        }

        public String getType() { return type; }
        public String getDescription() { return description; }
        public boolean isFulfilled() { return fulfilled; }
        public int getCurrent() { return current; }
        public int getRequired() { return required; }
    }
}
