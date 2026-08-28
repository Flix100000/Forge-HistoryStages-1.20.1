package net.bananemdnsa.historystages.data.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Reads and writes any {@link ModConfigSpec} as a flat map of dotted TOML paths.
 *
 * <p>Both directions walk the spec itself rather than a hand-maintained key list. Two such lists
 * used to exist ({@code CommonConfigSync}, {@code ClientConfigSync}) and both rotted: keys were
 * saveable but never synced, or declared in the editor with no case to apply them. A spec walk
 * cannot forget a key, because there is nothing to forget it in.
 */
public final class ConfigSpecCodec {

    /** Accepts everything the spec itself accepts. */
    public static final BiPredicate<String, String> NO_EXTRA_CHECK = (path, text) -> true;

    private ConfigSpecCodec() {}

    /** Snapshots every value in the spec, keyed by its dotted path, in declaration order. */
    public static Map<String, String> collect(ModConfigSpec spec) {
        Map<String, String> out = new LinkedHashMap<>();
        collect(spec.getValues(), "", out);
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
     * @param validate   when true, a value the spec rejects is skipped instead of written. Both
     *                   sides pass true: on the server it stops a modified client writing junk into
     *                   the file, on the client it stops a server pushing a value this client
     *                   cannot render. Unknown paths are dropped either way.
     * @param extraCheck a check the spec cannot make itself. A key declared with a plain
     *                   {@code define(...)} only gets an assignability check, so every string
     *                   passes — colours included. Pass {@link #NO_EXTRA_CHECK} when there is none.
     * @return how many values were applied
     */
    public static int apply(ModConfigSpec spec, Map<String, String> values, boolean validate,
                            BiPredicate<String, String> extraCheck) {
        int applied = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (applyOne(spec, entry.getKey(), entry.getValue(), validate, extraCheck)) applied++;
        }
        return applied;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean applyOne(ModConfigSpec spec, String path, String text, boolean validate,
                                    BiPredicate<String, String> extraCheck) {
        List<String> parts = Arrays.asList(path.split("\\."));
        Object raw = spec.getValues().getRaw(parts);
        if (!(raw instanceof ModConfigSpec.ConfigValue<?> value)) return false;

        Object parsed = parseLike(value.getDefault(), text);
        if (parsed == null) return false;
        if (validate && (!value.getSpec().test(parsed) || !extraCheck.test(path, text))) return false;

        ((ModConfigSpec.ConfigValue) value).set(parsed);
        return true;
    }

    /**
     * Parses a string into the type of the spec's own default value. Returns null when the text
     * cannot be read, in which case the caller keeps the current value — a server sending
     * something this client cannot parse is not worth failing a login over.
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
