package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

/**
 * An interaction-locked entity entry with an optional list of locked interaction actions.
 * When {@code lockActions} is null, all actions are locked (default behaviour).
 *
 * <p>In JSON the field is written as {@code unlock_actions} (the complement — actions that are
 * NOT locked). {@code null} / no field means all actions are locked.
 *
 * <p>The {@code other} action is a catch-all for any right-click interaction that does not map
 * to one of the specific gameplay actions.
 */
public class EntityInteractionLockEntry {

    /** Canonical ordered list of all recognised interaction actions. */
    public static final List<String> ALL_ACTIONS = List.of(
            "breed", "mount", "trade", "leash", "shear", "milk", "name", "equip", "other"
    );

    private final String id;
    private final List<String> lockActions; // null = all actions locked, empty = treated as all locked

    public EntityInteractionLockEntry(String id) {
        this(id, null);
    }

    public EntityInteractionLockEntry(String id, List<String> lockActions) {
        this.id = id;
        this.lockActions = (lockActions != null && !lockActions.isEmpty()) ? new ArrayList<>(lockActions) : null;
    }

    public String getId() { return id; }

    /** Returns null if all actions are locked, otherwise the explicit list of locked actions. */
    public List<String> getLockActions() { return lockActions; }

    public boolean hasLockActions() { return lockActions != null && !lockActions.isEmpty(); }

    /** True if the given action is blocked by this entry. Null filter blocks every action. */
    public boolean blocksAction(String action) {
        return lockActions == null || lockActions.contains(action);
    }

    public EntityInteractionLockEntry copy() {
        return new EntityInteractionLockEntry(id, lockActions != null ? new ArrayList<>(lockActions) : null);
    }
}
