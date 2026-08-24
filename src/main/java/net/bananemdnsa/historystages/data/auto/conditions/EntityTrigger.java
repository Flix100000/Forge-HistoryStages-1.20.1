package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;

public record EntityTrigger(
        @SerializedName("id") String id,
        @SerializedName("sub_mode") String subMode  // "any" | "kill" | "interact"; null → "any"
) implements TriggerCondition {

    public EntitySubMode resolvedSubMode() {
        return EntitySubMode.parse(subMode);
    }

    @Override public String type() { return "entity"; }

    @Override public long signature() { return defaultSignature(id + "|" + resolvedSubMode().serialize()); }
}
