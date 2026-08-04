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

    /**
     * Copy of this result with every {@link EntryResult#canDeposit()} forced to false.
     * Used for pedestal-free requests (e.g. from the stage graph): there is no pedestal to
     * deposit into, so offering a deposit affordance would be worse than offering none.
     * Fulfilment is untouched — {@code canDeposit} does not feed into it.
     */
    public DependencyResult withoutCanDeposit() {
        List<GroupResult> strippedGroups = new ArrayList<>(groups.size());
        for (GroupResult group : groups) {
            List<EntryResult> strippedEntries = new ArrayList<>(group.getEntries().size());
            for (EntryResult e : group.getEntries()) {
                strippedEntries.add(new EntryResult(e.getType(), e.getId(), e.getDescription(),
                        e.isFulfilled(), e.getCurrent(), e.getRequired(), e.getOriginalRequired(), false));
            }
            strippedGroups.add(new GroupResult(group.getLogic(), group.isFulfilled(), strippedEntries));
        }
        return new DependencyResult(fulfilled, strippedGroups);
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
        private final String type;        // "item", "stage", "individual_stage", "advancement", "xp_level", "entity_kill", "stat"
        private final String id;          // Machine ID (item ID, stage ID, "xp", etc.)
        private final String description; // Human-readable, e.g. "3x Iron Ingot"
        private final boolean fulfilled;
        private final int current;        // Current progress (e.g. deposited count)
        private final int required;       // Required amount (after booster reduction, if any)
        private final int originalRequired; // Required amount before booster reduction; 0 means "same as required"
        private final boolean canDeposit; // If true, show a deposit button (e.g. for consume-XP)

        public EntryResult(String type, String id, String description, boolean fulfilled,
                int current, int required, int originalRequired, boolean canDeposit) {
            this.type = type;
            this.id = id;
            this.description = description;
            this.fulfilled = fulfilled;
            this.current = current;
            this.required = required;
            this.originalRequired = originalRequired;
            this.canDeposit = canDeposit;
        }

        public EntryResult(String type, String id, String description, boolean fulfilled,
                int current, int required, boolean canDeposit) {
            this(type, id, description, fulfilled, current, required, 0, canDeposit);
        }

        public EntryResult(String type, String id, String description, boolean fulfilled,
                int current, int required) {
            this(type, id, description, fulfilled, current, required, 0, false);
        }

        public EntryResult(String type, String description, boolean fulfilled) {
            this(type, "", description, fulfilled, fulfilled ? 1 : 0, 1, 0, false);
        }

        public String getType() { return type; }
        public String getId() { return id; }
        public String getDescription() { return description; }
        public boolean isFulfilled() { return fulfilled; }
        public int getCurrent() { return current; }
        public int getRequired() { return required; }
        /** @return the original required amount if a booster reduced it, otherwise 0 (= same as required). */
        public int getOriginalRequired() { return originalRequired; }
        public boolean canDeposit() { return canDeposit; }
    }
}
