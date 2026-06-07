package net.bananemdnsa.historystages.data.auto.conditions;

import com.google.gson.annotations.SerializedName;

public record PlaytimeTrigger(@SerializedName("days") int days) implements TriggerCondition {
    /** Convenience: 1 in-game day = 24000 ticks. */
    public int requiredTicks() { return Math.max(0, days) * 24000; }

    @Override public String type() { return "playtime"; }
    @Override public long signature() {
        long h = type().hashCode() & 0xFFFFFFFFL;
        return h * 1099511628211L ^ (Integer.hashCode(Math.max(0, days)) & 0xFFFFFFFFL);
    }
}
