package net.bananemdnsa.historystages.data.dependency;

import com.google.gson.annotations.SerializedName;

public class EntityKillDep {
    @SerializedName("entity_id")
    private String entityId;

    private int count;

    public EntityKillDep() {
        this.count = 1;
    }

    public EntityKillDep(String entityId, int count) {
        this.entityId = entityId;
        this.count = Math.max(1, count);
    }

    public String getEntityId() { return entityId; }
    public int getCount() { return count; }

    public void setEntityId(String entityId) { this.entityId = entityId; }
    public void setCount(int count) { this.count = Math.max(1, count); }

    public EntityKillDep copy() {
        return new EntityKillDep(entityId, count);
    }
}
