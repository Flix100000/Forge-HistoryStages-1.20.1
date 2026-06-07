package net.bananemdnsa.historystages.data.auto.conditions;

/** Which player–entity interactions count as a match for an {@link EntityTrigger}. */
public enum EntitySubMode {
    ANY("any"),
    KILL("kill"),
    INTERACT("interact");

    private final String serialized;

    EntitySubMode(String serialized) { this.serialized = serialized; }

    public String serialize() { return serialized; }

    /** Returns {@link #ANY} for null or unknown values. */
    public static EntitySubMode parse(String raw) {
        if (raw == null) return ANY;
        for (EntitySubMode m : values()) {
            if (m.serialized.equalsIgnoreCase(raw)) return m;
        }
        return ANY;
    }
}
