package net.bananemdnsa.historystages.data.graph;

import java.util.List;

/**
 * One editable key of {@code graph.toml}, described well enough for the editor to render a
 * row without knowing anything about the graph.
 *
 * @param path      dotted toml path, e.g. {@code style.global.unlocked.shape}
 * @param kind      which control the row draws
 * @param defaultValue the spec's own default, as text — the reset button's source of truth
 * @param min       lower bound, or null when the key has no range
 * @param max       upper bound, or null when the key has no range
 * @param enumConstants allowed values for {@link Kind#ENUM}, empty otherwise
 * @param enumType  simple name of the enum type for {@link Kind#ENUM}, null otherwise. Needed
 *                  because constant names are not unique across the six enums — {@code SOLID}
 *                  is both an edge style and a canvas background, and they do not translate to
 *                  the same word in every language.
 */
public record GraphKey(String path, Kind kind, String defaultValue,
                       Double min, Double max, List<String> enumConstants, String enumType) {

    public enum Kind { BOOLEAN, INTEGER, DOUBLE, STRING, RICH_TEXT, COLOR, ENUM, TEXTURE }

    /** The last path segment — the leaf name shared by all six style blocks. */
    public String leaf() {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
