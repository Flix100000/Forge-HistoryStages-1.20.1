package net.bananemdnsa.historystages.data;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ItemEntry {

    private final String id;
    private final JsonObject nbt;
    private final List<String> lockActions; // null = all actions locked

    public ItemEntry(String id) {
        this.id = id;
        this.nbt = null;
        this.lockActions = null;
    }

    public ItemEntry(String id, JsonObject nbt) {
        this.id = id;
        this.nbt = nbt;
        this.lockActions = null;
    }

    public ItemEntry(String id, JsonObject nbt, List<String> lockActions) {
        this.id = id;
        this.nbt = nbt;
        this.lockActions = (lockActions != null && !lockActions.isEmpty()) ? new ArrayList<>(lockActions) : null;
    }

    public String getId() { return id; }
    public JsonObject getNbt() { return nbt; }
    public boolean hasNbt() { return nbt != null && nbt.size() > 0; }

    /** Returns null if all actions are locked, otherwise the explicit list of locked actions. */
    public List<String> getLockActions() { return lockActions; }

    public boolean hasLockActions() { return lockActions != null && !lockActions.isEmpty(); }

    public ItemEntry copy() {
        return new ItemEntry(
                id,
                nbt != null ? nbt.deepCopy() : null,
                lockActions != null ? new ArrayList<>(lockActions) : null
        );
    }
}
