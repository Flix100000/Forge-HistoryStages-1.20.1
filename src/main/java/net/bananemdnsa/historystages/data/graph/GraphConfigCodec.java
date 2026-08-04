package net.bananemdnsa.historystages.data.graph;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.bananemdnsa.historystages.GraphConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@code graph.toml} values as a flat map of dotted TOML paths.
 *
 * <p>Both directions walk the spec itself rather than a hand-maintained key list. That
 * list is what rotted in the common config, where several keys are saved server-side and
 * never reach clients.
 */
public final class GraphConfigCodec {

    private GraphConfigCodec() {}

    /** Snapshots every value in the graph spec, keyed by its dotted path, in declaration order. */
    public static Map<String, String> collect() {
        Map<String, String> out = new LinkedHashMap<>();
        collect(GraphConfig.GRAPH_SPEC.getValues(), "", out);
        return out;
    }

    private static void collect(UnmodifiableConfig config, String prefix, Map<String, String> out) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object raw = entry.getRawValue();
            if (raw instanceof UnmodifiableConfig nested) {
                collect(nested, path, out);
            } else if (raw instanceof ModConfigSpec.ConfigValue<?> value) {
                Object current = value.get();
                if (current != null) out.put(path, String.valueOf(current));
            }
        }
    }

    /**
     * Writes values into the spec.
     *
     * @param validate when true, a value the spec rejects is skipped instead of written. Both
     *                 sides pass true. On the server it stops a modified client writing junk
     *                 into graph.toml; on the client it stops a server pushing a value this
     *                 client cannot render, such as a NaN zoom that would blank the canvas.
     *                 Unknown paths are dropped either way, so nothing about forward
     *                 compatibility depends on this flag.
     * @return how many values were applied
     */
    public static int apply(Map<String, String> values, boolean validate) {
        int applied = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (applyOne(entry.getKey(), entry.getValue(), validate)) applied++;
        }
        return applied;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean applyOne(String path, String text, boolean validate) {
        List<String> parts = Arrays.asList(path.split("\\."));
        Object raw = GraphConfig.GRAPH_SPEC.getValues().getRaw(parts);
        if (!(raw instanceof ModConfigSpec.ConfigValue<?> value)) return false;

        Object parsed = parseLike(value.getDefault(), text);
        if (parsed == null) return false;
        if (validate && !accepts(path, parsed, text, value)) return false;

        ((ModConfigSpec.ConfigValue) value).set(parsed);
        return true;
    }

    /**
     * The spec's own check, plus the one it cannot make. {@code ValueSpec.test} enforces ranges
     * and enum membership, but a key declared with a plain {@code define(...)} only gets an
     * assignability check — so every string passes, colours included. Without the second half a
     * client could write "rgb(1,2,3)" into a colour key and turn those nodes black for everyone.
     */
    private static boolean accepts(String path, Object parsed, String text,
                                   ModConfigSpec.ConfigValue<?> value) {
        if (!value.getSpec().test(parsed)) return false;
        return !GraphConfigEntries.isColorPath(path) || GraphColors.isValid(text);
    }

    /**
     * Parses a string into the type of the spec's own default value. Returns null when the
     * text cannot be read, in which case the caller keeps the current value — a server
     * sending something this client cannot parse is not worth failing a login over.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseLike(Object template, String text) {
        try {
            if (template instanceof Boolean) return Boolean.parseBoolean(text);
            if (template instanceof Integer) return Integer.parseInt(text);
            if (template instanceof Long) return Long.parseLong(text);
            if (template instanceof Double) return Double.parseDouble(text);
            if (template instanceof Float) return Float.parseFloat(text);
            if (template instanceof Enum<?> constant) {
                return Enum.valueOf((Class<Enum>) constant.getDeclaringClass(), text);
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }
}
