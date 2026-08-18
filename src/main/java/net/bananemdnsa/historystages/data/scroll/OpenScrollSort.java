package net.bananemdnsa.historystages.data.scroll;

/**
 * In which order the open scroll lists a chapter's entries.
 *
 * <p>{@link #ALPHABETICAL} sorts by display name, hidden entries included. That theoretically
 * leaks the alphabetical position of a name the glyph font is meant to withhold; a pack that cares
 * uses {@link #DEFINED}. Deliberate, rather than maintaining two sort orders.
 */
public enum OpenScrollSort {

    /** The order the stage file lists them in. */
    DEFINED("defined"),
    /** By display name, case-insensitive. */
    ALPHABETICAL("alphabetical");

    private final String id;

    OpenScrollSort(String id) {
        this.id = id;
    }

    public String serialize() {
        return id;
    }

    public boolean sortsByName() {
        return this == ALPHABETICAL;
    }

    /** The order with this id, or {@link #DEFINED} for an unknown or missing one. */
    public static OpenScrollSort parse(String raw) {
        if (raw == null) return DEFINED;
        String trimmed = raw.trim();
        for (OpenScrollSort sort : values()) {
            if (sort.id.equalsIgnoreCase(trimmed)) return sort;
        }
        return DEFINED;
    }
}
