package net.bananemdnsa.historystages.data.scroll;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the common config's {@code openScrollChapters} list — which chapters the open
 * scroll document has, in which order, and how each one draws.
 *
 * <p>Same shape as {@link net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout}: a
 * Minecraft-free codec so the whole thing is unit-testable, and a forgiving parser so a
 * hand-edited or outdated config can never produce an empty document. Unknown ids are dropped,
 * missing ones are appended with their defaults.
 */
public final class OpenScrollChapters {

    private OpenScrollChapters() {}

    private static final Map<OpenScrollChapter, OpenScrollChapterMode> DEFAULT_MODES =
            new EnumMap<>(Map.of(
                    OpenScrollChapter.OVERVIEW, OpenScrollChapterMode.TEXT,
                    OpenScrollChapter.ITEMS, OpenScrollChapterMode.ICONS,
                    OpenScrollChapter.CREATURES, OpenScrollChapterMode.ICONS,
                    OpenScrollChapter.WORLD, OpenScrollChapterMode.TEXT));

    /** Every chapter enabled, in declaration order, with its natural mode. */
    public static List<OpenScrollChapterEntry> defaults() {
        List<OpenScrollChapterEntry> out = new ArrayList<>();
        for (OpenScrollChapter chapter : OpenScrollChapter.values()) {
            out.add(new OpenScrollChapterEntry(chapter, true, DEFAULT_MODES.get(chapter)));
        }
        return out;
    }

    /** The defaults as config strings, for {@code builder.defineList}. */
    public static List<String> defaultsEncoded() {
        return defaults().stream().map(OpenScrollChapters::encode).toList();
    }

    public static String encode(OpenScrollChapterEntry entry) {
        return entry.chapter().serialize() + "|" + entry.enabled() + "|" + entry.mode().serialize();
    }

    /**
     * Parses the config list. Order is kept, unknown ids and repeats are dropped, and any chapter
     * the list never mentions is appended in declaration order with its default.
     */
    public static List<OpenScrollChapterEntry> parse(List<? extends String> raw) {
        List<OpenScrollChapterEntry> out = new ArrayList<>();
        List<OpenScrollChapter> seen = new ArrayList<>();
        if (raw != null) {
            for (String line : raw) {
                OpenScrollChapterEntry entry = decode(line);
                if (entry == null || seen.contains(entry.chapter())) continue;
                seen.add(entry.chapter());
                out.add(entry);
            }
        }
        for (OpenScrollChapter chapter : OpenScrollChapter.values()) {
            if (seen.contains(chapter)) continue;
            out.add(new OpenScrollChapterEntry(chapter, true, DEFAULT_MODES.get(chapter)));
        }
        return out;
    }

    /** One line, or {@code null} when the id is missing or unknown. */
    private static OpenScrollChapterEntry decode(String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.split("\\|", -1);
        OpenScrollChapter chapter = OpenScrollChapter.parse(parts[0]);
        if (chapter == null) return null;
        // A broken flag counts as enabled: losing a chapter is worse than showing one too many.
        boolean enabled = parts.length < 2 || !"false".equalsIgnoreCase(parts[1].trim());
        OpenScrollChapterMode mode = parts.length < 3
                ? DEFAULT_MODES.get(chapter)
                : OpenScrollChapterMode.parse(parts[2], DEFAULT_MODES.get(chapter));
        return new OpenScrollChapterEntry(chapter, enabled, mode);
    }
}
