package net.bananemdnsa.historystages.data.auto.conditions;

import org.jetbrains.annotations.Nullable;

/**
 * The named parts of a Minecraft day, with the tick windows vanilla actually uses.
 *
 * <p>They exist so a pack author does not have to know that night starts at 13000.
 *
 * <p>Named the way the game names them — sunset and sunrise, not dusk and dawn. The two are
 * synonyms in English but only one pair appears anywhere in Minecraft, and a preset list that
 * invents its own vocabulary is one the reader has to translate back.
 */
public enum TimePreset {
    DAY("day", 0, 11999),
    SUNSET("sunset", 12000, 12999),
    NIGHT("night", 13000, 22999),
    SUNRISE("sunrise", 23000, 23999),
    /** The window comes from the trigger's own from/to; the bounds here are never read. */
    CUSTOM("custom", 0, 0);

    private final String serialized;
    private final int from;
    private final int to;

    TimePreset(String serialized, int from, int to) {
        this.serialized = serialized;
        this.from = from;
        this.to = to;
    }

    public String serialize() { return serialized; }

    public int from() { return from; }

    public int to() { return to; }

    /** Null for an unknown or missing value — a trigger without a preset never fires. */
    @Nullable
    public static TimePreset parse(String raw) {
        if (raw == null) return null;
        for (TimePreset p : values()) {
            if (p.serialized.equalsIgnoreCase(raw)) return p;
        }
        return null;
    }
}
