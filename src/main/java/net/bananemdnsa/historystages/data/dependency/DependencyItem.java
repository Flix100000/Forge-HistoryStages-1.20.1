package net.bananemdnsa.historystages.data.dependency;

import com.google.gson.JsonObject;

public class DependencyItem {
    private String id;
    private int count;
    private JsonObject nbt;

    public DependencyItem() {
        this.count = 1;
    }

    public DependencyItem(String id, int count) {
        this.id = id;
        this.count = Math.max(1, count);
    }

    public DependencyItem(String id, int count, JsonObject nbt) {
        this.id = id;
        this.count = Math.max(1, count);
        this.nbt = nbt;
    }

    public String getId() { return id; }
    public int getCount() { return count; }
    public JsonObject getNbt() { return nbt; }
    public boolean hasNbt() { return nbt != null && nbt.size() > 0; }

    public void setId(String id) { this.id = id; }
    public void setCount(int count) { this.count = Math.max(1, count); }
    public void setNbt(JsonObject nbt) { this.nbt = nbt; }

    public DependencyItem copy() {
        return new DependencyItem(id, count, nbt != null ? nbt.deepCopy() : null);
    }
}
