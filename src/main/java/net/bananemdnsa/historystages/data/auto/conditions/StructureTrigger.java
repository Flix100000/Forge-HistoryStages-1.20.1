package net.bananemdnsa.historystages.data.auto.conditions;

import com.google.gson.annotations.SerializedName;

public record StructureTrigger(@SerializedName("id") String id) implements TriggerCondition {
    @Override public String type() { return "structure"; }
    @Override public long signature() {
        long h = type().hashCode() & 0xFFFFFFFFL;
        return h * 1099511628211L ^ (String.valueOf(id).hashCode() & 0xFFFFFFFFL);
    }
}
