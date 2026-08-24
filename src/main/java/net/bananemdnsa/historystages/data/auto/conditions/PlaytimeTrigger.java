package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;

public record PlaytimeTrigger(@SerializedName("days") int days) implements TriggerCondition {
    private static final int TICKS_PER_DAY = 24000;

    /** Convenience: 1 in-game day = 24000 ticks. */
    public int requiredTicks() { return Math.max(0, days) * TICKS_PER_DAY; }

    @Override public String type() { return "playtime"; }
    @Override public long signature() {
        long h = type().hashCode() & 0xFFFFFFFFL;
        return h * FNV_PRIME_64 ^ (Integer.hashCode(Math.max(0, days)) & 0xFFFFFFFFL);
    }
}
