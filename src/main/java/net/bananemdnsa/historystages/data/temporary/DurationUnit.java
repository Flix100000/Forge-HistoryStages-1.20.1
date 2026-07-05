package net.bananemdnsa.historystages.data.temporary;

/**
 * Time unit for {@link TemporaryConfig} durations and cooldowns. Measured in
 * game ticks (20 ticks = 1 second), so the timer reflects server uptime
 * (global stages) or online time (individual stages), not wall-clock time.
 */
public enum DurationUnit {
    SECONDS("seconds", 20L),
    MINUTES("minutes", 20L * 60),
    HOURS("hours", 20L * 60 * 60),
    DAYS("days", 20L * 60 * 60 * 24);

    private final String serialized;
    private final long ticksPerUnit;

    DurationUnit(String serialized, long ticksPerUnit) {
        this.serialized = serialized;
        this.ticksPerUnit = ticksPerUnit;
    }

    public String serialize() {
        return serialized;
    }

    public long ticksPerUnit() {
        return ticksPerUnit;
    }

    /** Returns {@link #HOURS} if {@code raw} is null or unknown. */
    public static DurationUnit parse(String raw) {
        if (raw == null) return HOURS;
        for (DurationUnit u : values()) {
            if (u.serialized.equalsIgnoreCase(raw)) return u;
        }
        return HOURS;
    }

    /** True iff {@code raw} corresponds to a defined unit (used for warnings). */
    public static boolean isKnown(String raw) {
        if (raw == null) return true;
        for (DurationUnit u : values()) {
            if (u.serialized.equalsIgnoreCase(raw)) return true;
        }
        return false;
    }
}
