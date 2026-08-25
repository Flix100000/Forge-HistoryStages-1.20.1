package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;

public record BiomeTrigger(@SerializedName("id") String id) implements TriggerCondition {
    @Override public String type() { return "biome"; }
    @Override public long signature() { return defaultSignature(id); }
}
