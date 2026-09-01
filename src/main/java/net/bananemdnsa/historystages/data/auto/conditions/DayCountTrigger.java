package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;

/**
 * The world reaching a given day.
 *
 * <p>The world's day, not the player's time in it — {@link PlaytimeTrigger} already covers the
 * latter. Read from {@code getDayTime}, so {@code /time set} moves it, which is what a pack author
 * expects it to do.
 */
public record DayCountTrigger(@SerializedName("days") int days) implements TriggerCondition {

    private static final int TICKS_PER_DAY = 24000;

    public int requiredDays() { return Math.max(0, days); }

    public boolean matches(long dayTime) { return dayTime / TICKS_PER_DAY >= requiredDays(); }

    @Override public String type() { return "day_count"; }

    @Override public long signature() {
        long h = type().hashCode() & 0xFFFFFFFFL;
        return h * FNV_PRIME_64 ^ (Integer.hashCode(requiredDays()) & 0xFFFFFFFFL);
    }
}
