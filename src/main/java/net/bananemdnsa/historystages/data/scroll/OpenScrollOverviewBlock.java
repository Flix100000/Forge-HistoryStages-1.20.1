package net.bananemdnsa.historystages.data.scroll;

/**
 * The blocks of the open scroll's overview page, in their default reading order.
 *
 * <p>Free of Minecraft types for the same reason as {@link OpenScrollChapter}: this is parsed from
 * the common config and drives the screen, and both are easier to test without a running game.
 */
public enum OpenScrollOverviewBlock {

    /** The stage icon, 32x32, centred. */
    ICON("icon"),
    /** The stage's display name. */
    TITLE("title"),
    /** The description from {@code graph_stages.json}, wrapped. The only block that may split. */
    DESCRIPTION("description"),
    /** The item / creature / world tally. */
    COUNTS("counts");

    private final String id;

    OpenScrollOverviewBlock(String id) {
        this.id = id;
    }

    public String serialize() {
        return id;
    }

    /** The block with this id, or {@code null} for an unknown or missing one. */
    public static OpenScrollOverviewBlock parse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        for (OpenScrollOverviewBlock block : values()) {
            if (block.id.equalsIgnoreCase(trimmed)) return block;
        }
        return null;
    }
}
