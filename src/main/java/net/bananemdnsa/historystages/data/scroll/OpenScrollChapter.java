package net.bananemdnsa.historystages.data.scroll;

/**
 * The chapters of the open scroll document, in their default order.
 *
 * <p>Deliberately free of Minecraft types: this is parsed from the visual config and drives the
 * screen, and both of those are easier to test without a running game.
 */
public enum OpenScrollChapter {

    /** Stage icon, name, description and the counts. Always text. */
    OVERVIEW("overview"),
    /** Directly listed items plus the items behind the stage's item tags. */
    ITEMS("items"),
    /** Spawn, attack and interaction locks together. */
    CREATURES("creatures"),
    /** Dimensions, structures and biomes. Text only — none of them has an icon. */
    WORLD("world");

    private final String id;

    OpenScrollChapter(String id) {
        this.id = id;
    }

    public String serialize() {
        return id;
    }

    /** The chapter with this id, or {@code null} for an unknown or missing one. */
    public static OpenScrollChapter parse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        for (OpenScrollChapter chapter : values()) {
            if (chapter.id.equalsIgnoreCase(trimmed)) return chapter;
        }
        return null;
    }

    /** True when this chapter can only ever be text, whatever the config says. */
    public boolean isTextOnly() {
        return this == OVERVIEW || this == WORLD;
    }
}
