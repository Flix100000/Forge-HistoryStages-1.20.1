package net.bananemdnsa.historystages.research;

import java.util.Locale;

/** How a configured min pedestal tier should match against the actual pedestal tier. */
public enum TierMode {
    MIN,
    EXACT;

    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TierMode parse(String raw, TierMode fallback) {
        if (raw == null) return fallback;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "min" -> MIN;
            case "exact" -> EXACT;
            default -> fallback;
        };
    }
}
