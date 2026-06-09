package net.bananemdnsa.historystages.data.auto.conditions;

import com.google.gson.annotations.SerializedName;

public record BlockPlaceTrigger(@SerializedName("id") String id) implements TriggerCondition {
    @Override public String type() { return "block_place"; }
    @Override public long signature() { return defaultSignature(id); }
}
