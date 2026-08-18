package net.bananemdnsa.historystages.data.graph;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Strict {@code #RRGGBB} handling for the config editor.
 *
 * <p>Deliberately narrower than {@link ResolvedStyle#parseColor}, which accepts any
 * length of hex because a hand-written pack file is allowed to be sloppy. An editor
 * field must reject what it cannot show back to the user unchanged, so exactly six
 * digits count here.
 */
public final class GraphColors {

    private static final Pattern SIX_HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

    private GraphColors() {}

    /** @return 0xRRGGBB, or {@code fallback} when the text is not exactly six hex digits. */
    public static int parse(String text, int fallback) {
        if (!isValid(text)) return fallback;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        return Integer.parseInt(s, 16);
    }

    /** Formats 0xRRGGBB as {@code #RRGGBB}; any alpha bits are dropped. */
    public static String format(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static boolean isValid(String text) {
        return text != null && SIX_HEX.matcher(text.trim()).matches();
    }
}
