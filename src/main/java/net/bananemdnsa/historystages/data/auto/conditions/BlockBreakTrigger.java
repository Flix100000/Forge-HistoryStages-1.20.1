package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;

public record BlockBreakTrigger(@SerializedName("id") String id) implements TriggerCondition {
    @Override public String type() { return "block_break"; }
    @Override public long signature() { return defaultSignature(id); }
}
