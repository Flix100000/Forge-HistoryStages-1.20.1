package net.bananemdnsa.historystages.data.scroll;

/** How a chapter draws its entries. */
public enum OpenScrollChapterMode {

    ICONS("icons"),
    TEXT("text");

    private final String id;

    OpenScrollChapterMode(String id) {
        this.id = id;
    }

    public String serialize() {
        return id;
    }

    /** The mode with this id, or {@code fallback} when the value is missing or unreadable. */
    public static OpenScrollChapterMode parse(String raw, OpenScrollChapterMode fallback) {
        if (raw == null) return fallback;
        String trimmed = raw.trim();
        for (OpenScrollChapterMode mode : values()) {
            if (mode.id.equalsIgnoreCase(trimmed)) return mode;
        }
        return fallback;
    }
}
