package net.bananemdnsa.historystages.data.graph;

import net.bananemdnsa.historystages.util.DebugLogger;

import java.util.List;
import java.util.Locale;

/**
 * Checks a per-stage style override against the spec of the block it will be layered onto,
 * dropping any field that does not belong there.
 *
 * <p>{@code graph.toml} is protected by {@code ValueSpec.test} on its save path.
 * {@code graph_stages.json} has nothing of the kind, so this is the only thing standing between
 * a modified client and a {@code "size": 400} that wrecks a node for every player on the server.
 *
 * <p>Free of Minecraft and NeoForge imports on purpose — that is what lets JUnit reach it. The
 * spec knowledge arrives as {@link GraphKey}s from
 * {@code GraphConfigEntries.styleKeys}, which does the NeoForge-side walk.
 *
 * <p>A bad field is dropped, not fatal. Rejecting the whole payload would turn one bad value
 * into a silent no-op the author has no way to explain.
 */
public final class StageStyleValidator {

    private StageStyleValidator() {}

    /**
     * @param keys the {@link GraphKey}s of one style block; a leaf absent from this list is not
     *             editable and its value is dropped
     * @return a new style carrying only the fields that survived
     */
    public static StageStyle sanitize(StageStyle style, List<GraphKey> keys) {
        StageStyle out = new StageStyle();
        if (style == null || keys == null) return out;

        for (GraphKey key : keys) {
            String leaf = key.leaf();
            String value = StageStyleFields.get(style, leaf);
            if (value == null) continue;

            String checked = check(key, value);
            if (checked == null) {
                DebugLogger.error("Stage Graph",
                        "Dropped invalid style override " + leaf + " = " + value);
                continue;
            }
            StageStyleFields.set(out, leaf, checked);
        }
        return out;
    }

    /** The value to store, or null when it may not be stored at all. */
    private static String check(GraphKey key, String value) {
        return switch (key.kind()) {
            case ENUM -> {
                String upper = value.trim().toUpperCase(Locale.ROOT);
                yield key.enumConstants().contains(upper) ? upper : null;
            }
            case COLOR -> GraphColors.isValid(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
            case BOOLEAN -> {
                String lower = value.trim().toLowerCase(Locale.ROOT);
                yield ("true".equals(lower) || "false".equals(lower)) ? lower : null;
            }
            case INTEGER, DOUBLE -> inRange(key, value);
            // No style leaf is a plain string or a texture; if one ever is, it arrives unchecked
            // and this arm is where to add its rule.
            case STRING, TEXTURE -> value;
        };
    }

    private static String inRange(GraphKey key, String value) {
        double number;
        try {
            number = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        // NaN loses every comparison, so it would slip past both bounds below — and Gson takes
        // both NaN and "NaN" into a Double field, which puts it within reach of a modified
        // client. Math.round(NaN) is 0, i.e. a node collapsed to a dot for every player.
        if (!Double.isFinite(number)) return null;
        if (key.min() != null && number < key.min()) return null;
        if (key.max() != null && number > key.max()) return null;
        return value.trim();
    }
}
