package net.bananemdnsa.historystages.data;

/**
 * What happens to a research scroll once its research finishes.
 *
 * <ul>
 *   <li>{@link #CONSUME} — the scroll is used up. What the pedestal did before this
 *       option existed, and therefore the fallback for anything unreadable.</li>
 *   <li>{@link #REPLACE} — a fresh, ownerless scroll for the same stage is put back into
 *       the pedestal, so the next player can research it without a second copy.</li>
 *   <li>{@link #OPEN} — an open scroll is put into the pedestal as a keepsake. This ends
 *       the chain on purpose; there is no refill.</li>
 * </ul>
 */
public enum ScrollCompletion {
    CONSUME("consume"),
    REPLACE("replace"),
    OPEN("open");

    private final String serialized;

    ScrollCompletion(String serialized) {
        this.serialized = serialized;
    }

    public String serialize() {
        return serialized;
    }

    /** Returns {@link #CONSUME} if {@code raw} is null or unknown. */
    public static ScrollCompletion parse(String raw) {
        if (raw == null) return CONSUME;
        for (ScrollCompletion c : values()) {
            if (c.serialized.equalsIgnoreCase(raw)) return c;
        }
        return CONSUME;
    }

    /** True iff {@code raw} corresponds to a defined mode (used for warnings). */
    public static boolean isKnown(String raw) {
        if (raw == null) return true; // null = absent = default, not a typo
        for (ScrollCompletion c : values()) {
            if (c.serialized.equalsIgnoreCase(raw)) return true;
        }
        return false;
    }

    /**
     * The mode that actually applies: the stage's own override when it has a readable one,
     * otherwise the configured default. An unreadable override is treated as absent.
     */
    public static ScrollCompletion resolve(String stageOverride, String configDefault) {
        if (stageOverride != null && !stageOverride.isEmpty() && isKnown(stageOverride)) {
            return parse(stageOverride);
        }
        return parse(configDefault);
    }
}
