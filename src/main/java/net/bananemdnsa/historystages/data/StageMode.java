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
 * </ul>
 */
public enum StageMode {
    DEFAULT("default"),
    AUTO("auto"),
    EXTERNAL("external");

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
