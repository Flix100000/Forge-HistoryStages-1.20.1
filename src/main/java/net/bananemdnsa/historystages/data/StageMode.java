package net.bananemdnsa.historystages.data;

/**
 * Determines how a stage is unlocked and whether a research scroll is generated.
 *
 * <ul>
 *   <li>{@link #DEFAULT} — scroll generated, Pedestal research as today.</li>
 *   <li>{@link #AUTO} — no scroll generated. Unlock via {@code auto_trigger}
 *       discovery events.</li>
 *   <li>{@link #EXTERNAL} — scroll generated, but the Pedestal refuses to
 *       research it. Modpack devs unlock via {@code /stage unlock} or scripts.</li>
 *   <li>{@link #TEMPORARY} — no scroll generated. Like {@link #AUTO}, unlocks via
 *       {@code auto_trigger} discovery events, but re-locks automatically after a
 *       configured {@code temporary.duration}. Can optionally be re-triggered with
 *       a cooldown (see {@code temporary}).</li>
 * </ul>
 */
public enum StageMode {
    DEFAULT("default"),
    AUTO("auto"),
    EXTERNAL("external"),
    TEMPORARY("temporary");

    /** True iff this mode unlocks via {@code auto_trigger} discovery events (AUTO or TEMPORARY). */
    public boolean usesAutoTrigger() {
        return this == AUTO || this == TEMPORARY;
    }

    private final String serialized;

    StageMode(String serialized) {
        this.serialized = serialized;
    }

    public String serialize() {
        return serialized;
    }

    /** Returns {@link #DEFAULT} if {@code raw} is null or unknown. */
    public static StageMode parse(String raw) {
        if (raw == null) return DEFAULT;
        for (StageMode m : values()) {
            if (m.serialized.equalsIgnoreCase(raw)) return m;
        }
        return DEFAULT;
    }

    /** True iff {@code raw} corresponds to a defined mode (used for warnings). */
    public static boolean isKnown(String raw) {
        if (raw == null) return true; // null = absent = default, not a typo
        for (StageMode m : values()) {
            if (m.serialized.equalsIgnoreCase(raw)) return true;
        }
        return false;
    }
}
