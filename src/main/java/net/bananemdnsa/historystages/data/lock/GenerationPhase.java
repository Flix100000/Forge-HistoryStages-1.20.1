package net.bananemdnsa.historystages.data.lock;

/** Which lock phase of a stage a generation limit counts in. */
public enum GenerationPhase {
    /** Counts while the stage is locked; after unlocking the structure is unrestricted. */
    WHILE_LOCKED("while_locked"),
    /** Nothing generates while locked; the limit counts after the stage has been unlocked. */
    AFTER_UNLOCK("after_unlock");

    private final String serialized;

    GenerationPhase(String serialized) {
        this.serialized = serialized;
    }

    public String serialize() {
        return serialized;
    }

    /** Unknown or missing values fall back to {@link #WHILE_LOCKED} — that is the legacy meaning. */
    public static GenerationPhase parse(String raw) {
        if (raw != null) {
            for (GenerationPhase p : values()) {
                if (p.serialized.equals(raw)) return p;
            }
        }
        return WHILE_LOCKED;
    }
}
