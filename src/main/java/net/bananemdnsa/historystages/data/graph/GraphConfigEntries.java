package net.bananemdnsa.historystages.data.graph;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.bananemdnsa.historystages.GraphConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns {@link GraphConfig#GRAPH_SPEC} into the list of rows the editor draws.
 *
 * <p>Generated rather than hand-written: 93 hand-written entries would be a fourth place
 * where the graph's defaults live, and a key added later would silently never appear.
 */
public final class GraphConfigEntries {

    /** Top-level table that gets its own screen instead of rows in the tab. */
    public static final String STYLE = "style";

    /** Colour keys. String-typed, so nothing in the spec distinguishes them from text. */
    private static final Set<String> COLOR_PATHS = Set.of("edges.colorMet", "edges.colorOpen");
    private static final Set<String> COLOR_LEAVES = Set.of("border", "fill", "labelColor");

    /** Keys holding a texture path, likewise indistinguishable from plain text in the spec. */
    private static final Set<String> TEXTURE_PATHS = Set.of("canvas.backgroundTexture");

    /**
     * Leaves the editor does not offer, even though they are in the spec.
     *
     * <p>Only {@code cornerRadius} so far, and only because nothing draws with it: the rounded
     * shape's corners are baked into a texture generated once at a fixed relative radius, which
     * {@code NodeShapes.rounded} documents. The value is carried all the way through
     * {@code ResolvedStyle} and never read. Leaving it in a toml nobody reads is one thing;
     * giving it a control with a live preview next to it would promise an effect that does not
     * exist. Delete this entry the day the shape stops coming from a fixed texture.
     */
    private static final Set<String> HIDDEN_LEAVES = Set.of("cornerRadius");

    private GraphConfigEntries() {}

    /**
     * The tables shown as sections in the tab, in declaration order, without {@link #STYLE}.
     * Keyed by table name (e.g. {@code canvas}).
     */
    public static Map<String, List<GraphKey>> sections() {
        Map<String, List<GraphKey>> out = new LinkedHashMap<>();
        UnmodifiableConfig spec = GraphConfig.GRAPH_SPEC.getSpec();
        for (UnmodifiableConfig.Entry entry : spec.entrySet()) {
            String table = entry.getKey();
            if (STYLE.equals(table)) continue;
            if (!(entry.getRawValue() instanceof UnmodifiableConfig nested)) continue;
            List<GraphKey> keys = new ArrayList<>();
            collect(nested, table, keys);
            out.put(table, keys);
        }
        return out;
    }

    /**
     * The ten keys of one style block.
     *
     * @param collection {@code global} or {@code individual}
     * @param state      {@code unlocked}, {@code reachable} or {@code locked}
     */
    public static List<GraphKey> styleKeys(String collection, String state) {
        List<GraphKey> keys = new ArrayList<>();
        String prefix = STYLE + "." + collection + "." + state;
        Object raw = GraphConfig.GRAPH_SPEC.getSpec().getRaw(Arrays.asList(prefix.split("\\.")));
        if (raw instanceof UnmodifiableConfig nested) collect(nested, prefix, keys);
        return keys;
    }

    private static void collect(UnmodifiableConfig table, String prefix, List<GraphKey> out) {
        for (UnmodifiableConfig.Entry entry : table.entrySet()) {
            String path = prefix + "." + entry.getKey();
            Object raw = entry.getRawValue();
            if (raw instanceof UnmodifiableConfig nested) {
                collect(nested, path, out);
            } else if (raw instanceof ModConfigSpec.ValueSpec valueSpec) {
                if (HIDDEN_LEAVES.contains(entry.getKey())) continue;
                out.add(describe(path, valueSpec));
            }
        }
    }

    private static GraphKey describe(String path, ModConfigSpec.ValueSpec spec) {
        Object def = spec.getDefault();
        GraphKey.Kind kind = kindOf(path, def);

        Double min = null;
        Double max = null;
        ModConfigSpec.Range<?> range = spec.getRange();
        if (range != null && range.getMin() instanceof Number lo && range.getMax() instanceof Number hi) {
            min = lo.doubleValue();
            max = hi.doubleValue();
        }

        List<String> constants = List.of();
        String enumType = null;
        if (kind == GraphKey.Kind.ENUM) {
            Class<?> declaring = ((Enum<?>) def).getDeclaringClass();
            constants = Arrays.stream(declaring.getEnumConstants())
                    .map(c -> ((Enum<?>) c).name())
                    .toList();
            enumType = declaring.getSimpleName();
        }

        return new GraphKey(path, kind, String.valueOf(def), min, max, constants, enumType);
    }

    /**
     * Derived from the default value's runtime class, not from {@code ValueSpec.getClazz()} —
     * that only gets set by defineEnum and defineInRange and reports Object for everything
     * declared with a plain define(...).
     */
    private static GraphKey.Kind kindOf(String path, Object def) {
        if (def instanceof Boolean) return GraphKey.Kind.BOOLEAN;
        if (def instanceof Integer || def instanceof Long) return GraphKey.Kind.INTEGER;
        if (def instanceof Double || def instanceof Float) return GraphKey.Kind.DOUBLE;
        if (def instanceof Enum<?>) return GraphKey.Kind.ENUM;

        if (isColorPath(path)) return GraphKey.Kind.COLOR;
        if (TEXTURE_PATHS.contains(path)) return GraphKey.Kind.TEXTURE;
        return GraphKey.Kind.STRING;
    }

    /**
     * Whether a path holds a {@code #RRGGBB} colour. Public because the codec needs it too:
     * colours are String-typed, and a plain {@code define(...)} installs a validator that only
     * checks assignability, so {@code ValueSpec.test} passes any string at all.
     */
    public static boolean isColorPath(String path) {
        String leaf = path.substring(path.lastIndexOf('.') + 1);
        return COLOR_PATHS.contains(path)
                || (path.startsWith(STYLE + ".") && COLOR_LEAVES.contains(leaf));
    }
}
