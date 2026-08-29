package net.bananemdnsa.historystages.data.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;

import java.util.Map;

/**
 * Reads and writes {@code graph.toml} values as a flat map of dotted TOML paths.
 *
 * <p>Both directions walk the spec itself rather than a hand-maintained key list. Two such lists
 * used to exist for the other configs and both rotted — keys were saved server-side and never
 * reached a single client. They are gone now; {@link ConfigSpecCodec} does this for every spec.
 */
public final class GraphConfigCodec {

    private GraphConfigCodec() {}

    /**
     * The check the spec cannot make: a colour key is declared with a plain {@code define(...)},
     * so every string passes its own test. Without this a client could write "rgb(1,2,3)" into a
     * colour key and turn those nodes black for everyone.
     */
    private static final java.util.function.BiPredicate<String, String> COLOR_CHECK =
            (path, text) -> !GraphConfigEntries.isColorPath(path) || GraphColors.isValid(text);

    /** Snapshots every value in the graph spec, keyed by its dotted path, in declaration order. */
    public static Map<String, String> collect() {
        return ConfigSpecCodec.collect(GraphConfig.GRAPH_SPEC);
    }

    /** @see ConfigSpecCodec#apply */
    public static int apply(Map<String, String> values, boolean validate) {
        return ConfigSpecCodec.apply(GraphConfig.GRAPH_SPEC, values, validate, COLOR_CHECK);
    }
}
