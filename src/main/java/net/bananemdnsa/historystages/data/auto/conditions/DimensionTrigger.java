package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;

public record DimensionTrigger(@SerializedName("id") String id) implements TriggerCondition {
    @Override public String type() { return "dimension"; }
    @Override public long signature() { return defaultSignature(id); }
}
