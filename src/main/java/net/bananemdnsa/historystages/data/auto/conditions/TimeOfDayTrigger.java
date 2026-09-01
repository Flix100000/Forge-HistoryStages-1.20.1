package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * The time of day, either as one of the named windows or as a window of the author's own.
 *
 * <p>{@code from} and {@code to} are boxed so a preset can leave them out of the file entirely —
 * Gson drops a null field, and two zeroes next to {@code "preset": "night"} would only invite the
 * next reader to wonder which of the two the game believes.
 */
public record TimeOfDayTrigger(
        @SerializedName("preset") String preset,
        @SerializedName("from") Integer from,
        @SerializedName("to") Integer to
) implements TriggerCondition {

    public static final int TICKS_PER_DAY = 24000;

    /** One of the named windows. */
    public static TimeOfDayTrigger of(TimePreset preset) {
        return new TimeOfDayTrigger(preset.serialize(), null, null);
    }

    /** A window of the author's own; {@code from > to} runs across midnight. */
    public static TimeOfDayTrigger custom(int from, int to) {
        return new TimeOfDayTrigger(TimePreset.CUSTOM.serialize(), from, to);
    }

    /** Null when this build does not know the preset; such a trigger never fires. */
    @Nullable
    public TimePreset resolvedPreset() { return TimePreset.parse(preset); }

    public int windowFrom() {
        TimePreset resolved = resolvedPreset();
        return resolved == null || resolved == TimePreset.CUSTOM ? clamp(from) : resolved.from();
    }

    public int windowTo() {
        TimePreset resolved = resolvedPreset();
        return resolved == null || resolved == TimePreset.CUSTOM ? clamp(to) : resolved.to();
    }

    private static int clamp(Integer value) {
        int v = value == null ? 0 : value;
        return Math.max(0, Math.min(TICKS_PER_DAY - 1, v));
    }

    public boolean matches(long dayTime) {
        if (resolvedPreset() == null) return false;
        int now = Math.floorMod(dayTime, TICKS_PER_DAY);
        int start = windowFrom();
        int end = windowTo();
        // start > end is a window across midnight (22000-2000), not an empty one.
        return start <= end ? (now >= start && now <= end) : (now >= start || now <= end);
    }

    @Override public String type() { return "world_time"; }

    @Override public long signature() {
        TimePreset resolved = resolvedPreset();
        String key = resolved == null ? String.valueOf(preset) : resolved.serialize();
        return defaultSignature(key + "|" + windowFrom() + "|" + windowTo());
    }
}
