package net.bananemdnsa.historystages.data.scroll;

/**
 * What a reader sees in the open scroll document for a stage they have not unlocked.
 *
 * <p>{@link #OBSCURED} is the default on purpose: an unreadable config value must not turn a
 * pack's carefully hidden content into a public list.
 */
public enum OpenScrollVisibility {

    /** Everything readable, whoever holds the scroll. The document is a record. */
    VISIBLE("visible"),
    /** Locked entries as silhouettes, their names in the Standard Galactic font. */
    OBSCURED("obscured");

    private final String id;

    OpenScrollVisibility(String id) {
        this.id = id;
    }

    public String serialize() {
        return id;
    }

    public boolean hidesLocked() {
        return this == OBSCURED;
    }

    public static OpenScrollVisibility parse(String raw) {
        if (raw != null) {
            String trimmed = raw.trim();
            for (OpenScrollVisibility value : values()) {
                if (value.id.equalsIgnoreCase(trimmed)) return value;
            }
        }
        return OBSCURED;
    }
}
