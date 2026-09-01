package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;

/**
 * A status effect being applied to the player.
 *
 * <p>Event-driven, not polled: this fires when the effect is <em>given</em>. Someone who already
 * has it when the stage is written waits for the next application, which is the honest reading of
 * "gets the effect" and keeps the trigger on a single code path.
 */
public record EffectTrigger(@SerializedName("id") String id) implements TriggerCondition {
    @Override public String type() { return "effect"; }
    @Override public long signature() { return defaultSignature(id); }
}
