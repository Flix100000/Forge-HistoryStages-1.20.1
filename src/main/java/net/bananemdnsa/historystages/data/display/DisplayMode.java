package net.bananemdnsa.historystages.data.display;

import java.util.Locale;

/**
 * How a stage modifies a locked item's display.
 * <ul>
 *     <li>{@link #OFF} — no change (default).</li>
 *     <li>{@link #HIDDEN} — name becomes empty / tooltip extra lines are stripped.</li>
 *     <li>{@link #REPLACE} — shown with custom text.</li>
 * </ul>
 */
public enum DisplayMode {
    OFF,
    HIDDEN,
    REPLACE;

    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DisplayMode parse(String raw, DisplayMode fallback) {
        if (raw == null) return fallback;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> OFF;
            case "hidden" -> HIDDEN;
            case "replace" -> REPLACE;
            default -> fallback;
        };
    }
}
