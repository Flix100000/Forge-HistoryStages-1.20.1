package net.bananemdnsa.historystages.client.editor;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Single source of truth for the client config values the editor's Client tab edits.
 * <p>
 * The counterpart to {@link net.bananemdnsa.historystages.network.CommonConfigSync}, minus the
 * wire: client.toml belongs to the player alone and never travels between server and client. What
 * carries over is the reason that class exists — the Client tab used to apply its values through a
 * hand-written {@code switch}, and two rows ({@code showDependenciesOnScroll} and
 * {@code hideFulfilledDependencies}) were declared in the editor but never given a case, so
 * toggling them in the GUI did nothing at all. Registering a setting here is the only step, and
 * {@link #verifyCoversEveryClientValue()} shouts if a new one is forgotten.
 * <p>
 * The keys are the field names in {@link Config.Client}, which is also what the editor's
 * {@code ConfigEntry.key} uses — the reflection check below relies on those two staying the same.
 */
public final class ClientConfigSync {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** One client value: how to read it as the editor's string, and how to write a string back. */
    private record Entry(Supplier<String> read, Consumer<String> write) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private ClientConfigSync() {}

    static {
        // visuals
        bool("showTooltips", Config.CLIENT.showTooltips);
        bool("showStageName", Config.CLIENT.showStageName);
        bool("showAllUntilComplete", Config.CLIENT.showAllUntilComplete);
        bool("showLockIcons", Config.CLIENT.showLockIcons);
        bool("showBoosterTooltips", Config.CLIENT.showBoosterTooltips);
        bool("showScrollTierTooltip", Config.CLIENT.showScrollTierTooltip);
        integer("openScrollBackdrop", Config.CLIENT.openScrollBackdrop);

        // jade
        bool("jadeShowInfo", Config.CLIENT.jadeShowInfo);
        bool("jadeStageName", Config.CLIENT.jadeStageName);
        bool("jadeShowAllUntilComplete", Config.CLIENT.jadeShowAllUntilComplete);

        // individual_stages
        bool("showSilverLockIcons", Config.CLIENT.showSilverLockIcons);
        bool("showIndividualTooltips", Config.CLIENT.showIndividualTooltips);

        // dimension_lock
        bool("dimUseActionbar", Config.CLIENT.dimUseActionbar);
        bool("dimShowChat", Config.CLIENT.dimShowChat);
        bool("dimShowStagesInChat", Config.CLIENT.dimShowStagesInChat);

        // mob_lock
        bool("mobUseActionbar", Config.CLIENT.mobUseActionbar);
        bool("mobShowChat", Config.CLIENT.mobShowChat);
        bool("mobShowStagesInChat", Config.CLIENT.mobShowStagesInChat);

        // structure_visuals
        bool("structureBorderEnabled", Config.CLIENT.structureBorderEnabled);
        dbl("structureBorderDistance", Config.CLIENT.structureBorderDistance);
        bool("structureLockOverlayEnabled", Config.CLIENT.structureLockOverlayEnabled);
        dbl("structureLockOverlayOpacity", Config.CLIENT.structureLockOverlayOpacity);

        // dependencies
        bool("showDependenciesOnScroll", Config.CLIENT.showDependenciesOnScroll);
        bool("hideFulfilledDependencies", Config.CLIENT.hideFulfilledDependencies);

        // jei_hiding (Issue #64)
        bool("hideLockedItemsInJei", Config.CLIENT.hideLockedItemsInJei);
        bool("hideLockedRecipesInJei", Config.CLIENT.hideLockedRecipesInJei);
        enumValue("lockedItemMultiStagePolicy", Config.CLIENT.lockedItemMultiStagePolicy,
                Config.Client.MultiStagePolicy.class);

        verifyCoversEveryClientValue();
    }

    /**
     * Reflects over {@link Config.Client} and complains about any value that was never registered
     * above. Logs rather than throws: a forgotten setting is a dead row in the editor, not a reason
     * to take the game down.
     */
    private static void verifyCoversEveryClientValue() {
        List<String> missing = Arrays.stream(Config.Client.class.getDeclaredFields())
                .filter(field -> ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType()))
                .map(java.lang.reflect.Field::getName)
                .filter(name -> !ENTRIES.containsKey(name))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            LOGGER.error("[HistoryStages] Config values missing from ClientConfigSync — editing these "
                    + "in the config editor does nothing: {}", missing);
        }
    }

    // --- registration helpers ---

    private static void register(String key, Supplier<String> read, Consumer<String> write) {
        if (ENTRIES.put(key, new Entry(read, write)) != null) {
            throw new IllegalStateException("Duplicate client config key: " + key);
        }
    }

    private static void bool(String key, ForgeConfigSpec.BooleanValue value) {
        register(key, () -> value.get().toString(), s -> value.set(Boolean.parseBoolean(s)));
    }

    private static void integer(String key, ForgeConfigSpec.IntValue value) {
        register(key, () -> value.get().toString(), s -> {
            try {
                value.set(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                LOGGER.warn("[HistoryStages] Ignoring malformed integer for config key '{}': {}", key, s);
            }
        });
    }

    private static void dbl(String key, ForgeConfigSpec.DoubleValue value) {
        register(key, () -> value.get().toString(), s -> {
            try {
                value.set(Double.parseDouble(s.trim()));
            } catch (NumberFormatException e) {
                LOGGER.warn("[HistoryStages] Ignoring malformed number for config key '{}': {}", key, s);
            }
        });
    }

    private static <E extends Enum<E>> void enumValue(String key, ForgeConfigSpec.EnumValue<E> value,
                                                      Class<E> type) {
        register(key, () -> value.get().name(), s -> {
            try {
                value.set(Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[HistoryStages] Ignoring unknown value for config key '{}': {}", key, s);
            }
        });
    }

    // --- public API ---

    /** Reads the current client config into the editor's string form, keyed by field name. */
    public static Map<String, String> readAll() {
        Map<String, String> values = new LinkedHashMap<>();
        ENTRIES.forEach((key, entry) -> values.put(key, entry.read().get()));
        return values;
    }

    /**
     * Writes the editor's values back into the client config. Unknown keys are logged rather than
     * dropped silently — a silent drop is exactly how the two dead rows stayed invisible.
     * <p>
     * This only touches the in-memory spec; persisting is {@code Config.CLIENT_SPEC.save()}, which
     * the caller does once for the whole batch.
     */
    public static void applyAll(Map<String, String> values) {
        for (Map.Entry<String, String> incoming : values.entrySet()) {
            Entry entry = ENTRIES.get(incoming.getKey());
            if (entry == null) {
                LOGGER.warn("[HistoryStages] Unknown client config key '{}' — ignored.", incoming.getKey());
                continue;
            }
            entry.write().accept(incoming.getValue());
        }
    }
}
