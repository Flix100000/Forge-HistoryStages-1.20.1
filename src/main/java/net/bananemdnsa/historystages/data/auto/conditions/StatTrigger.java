package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * A vanilla statistic reaching a threshold — 50 uses of a diamond pickaxe, 500 blocks mined, one
 * night spent in a bed.
 *
 * <p>The generic one. Fishing, riding, distance walked, items crafted and mobs killed are all
 * statistics, so none of them needs a trigger type of its own.
 */
public record StatTrigger(
        @SerializedName("category") String category,
        @SerializedName("id") String id,
        @SerializedName("count") int count
) implements TriggerCondition {

    /** Null when this build does not know the category; such a trigger never fires. */
    @Nullable
    public StatCategory resolvedCategory() { return StatCategory.parse(category); }

    /** At least 1 — a threshold of 0 is met by everyone before they have done anything. */
    public int requiredCount() { return Math.max(1, count); }

    public boolean matches(int statValue) { return statValue >= requiredCount(); }

    @Override public String type() { return "stat"; }

    @Override public long signature() {
        StatCategory resolved = resolvedCategory();
        String categoryKey = resolved == null ? String.valueOf(category) : resolved.serialize();
        return defaultSignature(categoryKey + "|" + id + "|" + requiredCount());
    }
}
