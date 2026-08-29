package net.bananemdnsa.historystages.data.scroll;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the visual config's {@code open_scroll.overviewBlocks} list — which blocks the
 * overview page has and in which order.
 *
 * <p>Same forgiving shape as {@link OpenScrollChapters}: unknown ids and repeats are dropped, and
 * any block the list never mentions is appended in declaration order, so a hand-edited or outdated
 * config can never produce an empty overview page.
 */
public final class OpenScrollOverviewBlocks {

    private OpenScrollOverviewBlocks() {}

    /** Every block enabled, in declaration order. */
    public static List<OpenScrollOverviewBlockEntry> defaults() {
        List<OpenScrollOverviewBlockEntry> out = new ArrayList<>();
        for (OpenScrollOverviewBlock block : OpenScrollOverviewBlock.values()) {
            out.add(new OpenScrollOverviewBlockEntry(block, true));
        }
        return out;
    }

    /** The defaults as config strings, for {@code builder.defineList}. */
    public static List<String> defaultsEncoded() {
        return defaults().stream().map(OpenScrollOverviewBlocks::encode).toList();
    }

    public static String encode(OpenScrollOverviewBlockEntry entry) {
        return entry.block().serialize() + "|" + entry.enabled();
    }

    /**
     * Parses the config list. Order is kept, unknown ids and repeats are dropped, and any block the
     * list never mentions is appended in declaration order.
     */
    public static List<OpenScrollOverviewBlockEntry> parse(List<? extends String> raw) {
        List<OpenScrollOverviewBlockEntry> out = new ArrayList<>();
        List<OpenScrollOverviewBlock> seen = new ArrayList<>();
        if (raw != null) {
            for (String line : raw) {
                OpenScrollOverviewBlockEntry entry = decode(line);
                if (entry == null || seen.contains(entry.block())) continue;
                seen.add(entry.block());
                out.add(entry);
            }
        }
        for (OpenScrollOverviewBlock block : OpenScrollOverviewBlock.values()) {
            if (seen.contains(block)) continue;
            out.add(new OpenScrollOverviewBlockEntry(block, true));
        }
        return out;
    }

    /** One line, or {@code null} when the id is missing or unknown. */
    private static OpenScrollOverviewBlockEntry decode(String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.split("\\|", -1);
        OpenScrollOverviewBlock block = OpenScrollOverviewBlock.parse(parts[0]);
        if (block == null) return null;
        // A broken flag counts as enabled: losing a block is worse than showing one too many.
        boolean enabled = parts.length < 2 || !"false".equalsIgnoreCase(parts[1].trim());
        return new OpenScrollOverviewBlockEntry(block, enabled);
    }
}
