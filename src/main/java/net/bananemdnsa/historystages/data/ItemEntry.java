package net.bananemdnsa.historystages.data;

import com.google.gson.JsonObject;

public class ItemEntry {
    private final String id;
    private final JsonObject nbt;

    public ItemEntry(String id) {
        this.id = id;
        this.nbt = null;
    }

    public ItemEntry(String id, JsonObject nbt) {
        this.id = id;
        this.nbt = nbt;
    }

    public String getId() { return id; }
    public JsonObject getNbt() { return nbt; }
    public boolean hasNbt() { return nbt != null && nbt.size() > 0; }

    public ItemEntry copy() {
        return new ItemEntry(id, nbt != null ? nbt.deepCopy() : null);
    }
}
