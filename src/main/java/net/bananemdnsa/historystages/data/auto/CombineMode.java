package net.bananemdnsa.historystages.data.auto;

/**
 * How the sub-triggers of an {@link AutoTrigger} combine to satisfy the parent.
 * Default is {@link #ANY}.
 */
public enum CombineMode {
    ANY("any"),
    ALL("all");

    private final String serialized;

    CombineMode(String serialized) { this.serialized = serialized; }

    public String serialize() { return serialized; }

    /** Returns {@link #ANY} for null or unknown values. */
    public static CombineMode parse(String raw) {
        if (raw == null) return ANY;
        for (CombineMode m : values()) {
            if (m.serialized.equalsIgnoreCase(raw)) return m;
        }
        return ANY;
    }
}
