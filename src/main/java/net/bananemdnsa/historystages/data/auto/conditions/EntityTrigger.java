package net.bananemdnsa.historystages.data.auto.conditions;

import com.google.gson.annotations.SerializedName;

public record EntityTrigger(
        @SerializedName("id") String id,
        @SerializedName("sub_mode") String subMode  // "any" | "kill" | "interact"; null → "any"
) implements TriggerCondition {

    public String resolvedSubMode() {
        if (subMode == null) return "any";
        String s = subMode.toLowerCase();
        return (s.equals("kill") || s.equals("interact")) ? s : "any";
    }

    @Override public String type() { return "entity"; }

    @Override public long signature() {
        long h = type().hashCode() & 0xFFFFFFFFL;
        String composite = String.valueOf(id) + "|" + resolvedSubMode();
        return h * 1099511628211L ^ (composite.hashCode() & 0xFFFFFFFFL);
    }
}
