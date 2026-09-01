package net.bananemdnsa.historystages.data.auto.conditions;

import org.jetbrains.annotations.Nullable;

/**
 * Which of Minecraft's stat types a {@link StatTrigger} reads.
 *
 * <p>Deliberately free of any {@code net.minecraft} reference. The mapping to a real
 * {@code StatType} lives in the event bridge, which is the only side that has a player to read a
 * value from — and keeping it out of here is what lets the matching logic be unit-tested at all.
 */
public enum StatCategory {
    CUSTOM("custom"),
    MINED("mined"),
    CRAFTED("crafted"),
    USED("used"),
    BROKEN("broken"),
    PICKED_UP("picked_up"),
    DROPPED("dropped"),
    KILLED("killed"),
    KILLED_BY("killed_by");

    private final String serialized;

    StatCategory(String serialized) { this.serialized = serialized; }

    public String serialize() { return serialized; }

    /**
     * Null for an unknown or missing value — deliberately not a default.
     *
     * <p>{@link EntitySubMode#parse} falls back to {@code ANY} because every one of its values is a
     * widening of the others. Here a typo would silently point at a different statistic, so the
     * honest answer is "no category", and a trigger without one never fires.
     */
    @Nullable
    public static StatCategory parse(String raw) {
        if (raw == null) return null;
        for (StatCategory c : values()) {
            if (c.serialized.equalsIgnoreCase(raw)) return c;
        }
        return null;
    }
}
