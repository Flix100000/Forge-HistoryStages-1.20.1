package net.bananemdnsa.historystages.network;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.Config;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Single source of truth for the common config values that travel between server and client.
 * <p>
 * Every entry declares both directions at once — how the value is read into the wire string and
 * how a wire string is written back. That is the whole point of this class: the server-to-client
 * map ({@code SyncConfigPacket.fromServerConfig}) and the apply path
 * ({@code SaveConfigPacket.applyCommonConfig}) used to be two hand-maintained lists, and they
 * drifted apart repeatedly — 28 keys the editor could change were never sent back to clients.
 * Adding a setting here covers both directions, so that class of bug cannot come back.
 * <p>
 * The wire keys are flat, hand-invented names ({@code structureCheckInterval}) that deliberately
 * do NOT match the toml paths ({@code structure_lock.checkInterval}). They are the packet's
 * protocol and the keys the config editor sends, so they are kept verbatim; renaming them to the
 * toml paths would break every client that has not updated in lockstep. List values keep their
 * ad-hoc separators for the same reason ({@code ;} for boosters and effects, {@code ,} for
 * replacements).
 */
public final class CommonConfigSync {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** One synced value: how to read it as a string, and how to write a string back into it. */
    private record Entry(Supplier<String> read, Consumer<String> write) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private CommonConfigSync() {}

    static {
        // messages
        bool("showWelcomeMessage", Config.COMMON.showWelcomeMessage);
        bool("showDebugErrors", Config.COMMON.showDebugErrors);
        bool("enableRuntimeLogging", Config.COMMON.enableRuntimeLogging);

        // gameplay
        bool("lockMobLoot", Config.COMMON.lockMobLoot);
        bool("lockBlockBreaking", Config.COMMON.lockBlockBreaking);
        dbl("lockedBlockBreakSpeedMultiplier", Config.COMMON.lockedBlockBreakSpeedMultiplier);
        bool("lockItemUsage", Config.COMMON.lockItemUsage);
        bool("lockEntityItems", Config.COMMON.lockEntityItems);
        bool("lockBlockInteraction", Config.COMMON.lockBlockInteraction);
        bool("lockContainerInteraction", Config.COMMON.lockContainerInteraction);
        bool("lockEnchanting", Config.COMMON.lockEnchanting);

        // notifications
        bool("broadcastChat", Config.COMMON.broadcastChat);
        str("unlockMessageFormat", Config.COMMON.unlockMessageFormat);
        bool("useActionbar", Config.COMMON.useActionbar);
        bool("useSounds", Config.COMMON.useSounds);
        bool("useToasts", Config.COMMON.useToasts);
        str("defaultStageIcon", Config.COMMON.defaultStageIcon);

        // research
        integer("researchTimeInSeconds", Config.COMMON.researchTimeInSeconds);
        bool("showDependencyScreenInPedestal", Config.COMMON.showDependencyScreenInPedestal);
        list("researchBoosters", Config.COMMON.researchBoosters, ";",
                net.bananemdnsa.historystages.research.ResearchBoosterRegistry::rebuildFromConfig);
        bool("lockScrollWhileResearching", Config.COMMON.lockScrollWhileResearching);
        str("defaultScrollCompletion", Config.COMMON.defaultScrollCompletion);

        // loot_replacements
        bool("useReplacements", Config.COMMON.useReplacements);
        list("replacementItems", Config.COMMON.replacementItems, ",", null);
        list("replacementTags", Config.COMMON.replacementTags, ",", null);

        // individual_stages
        bool("individualLockItemPickup", Config.COMMON.individualLockItemPickup);
        bool("individualLockLoot", Config.COMMON.individualLockLoot);
        bool("individualDropOnRevoke", Config.COMMON.individualDropOnRevoke);
        bool("individualLockBlockBreaking", Config.COMMON.individualLockBlockBreaking);
        dbl("individualLockedBlockBreakSpeedMultiplier", Config.COMMON.individualLockedBlockBreakSpeedMultiplier);
        bool("individualLockItemUsage", Config.COMMON.individualLockItemUsage);
        bool("individualLockBlockInteraction", Config.COMMON.individualLockBlockInteraction);
        bool("individualLockEnchanting", Config.COMMON.individualLockEnchanting);
        bool("individualBroadcastChat", Config.COMMON.individualBroadcastChat);
        str("individualUnlockMessageFormat", Config.COMMON.individualUnlockMessageFormat);
        bool("individualUseActionbar", Config.COMMON.individualUseActionbar);
        bool("individualUseSounds", Config.COMMON.individualUseSounds);
        bool("individualUseToasts", Config.COMMON.individualUseToasts);

        // structure_lock
        integer("structureCheckInterval", Config.COMMON.structureCheckInterval);
        bool("structureMessageEnabled", Config.COMMON.structureMessageEnabled);
        str("structureLockMessageFormat", Config.COMMON.structureLockMessageFormat);
        bool("structureLockInChat", Config.COMMON.structureLockInChat);
        bool("structureDamageEnabled", Config.COMMON.structureDamageEnabled);
        dbl("structureDamageAmount", Config.COMMON.structureDamageAmount);
        integer("structureDamageInterval", Config.COMMON.structureDamageInterval);
        integer("structureLockPadding", Config.COMMON.structureLockPadding);
        integer("structureClusterDistance", Config.COMMON.structureClusterDistance);
        bool("structureBlockRightClick", Config.COMMON.structureBlockRightClick);
        bool("structureBlockLeftClick", Config.COMMON.structureBlockLeftClick);
        bool("structureBlockProjectiles", Config.COMMON.structureBlockProjectiles);

        // biome_lock
        integer("biomeCheckInterval", Config.COMMON.biomeCheckInterval);
        bool("biomeEffectsEnabled", Config.COMMON.biomeEffectsEnabled);
        list("biomeEffects", Config.COMMON.biomeEffects, ";",
                net.bananemdnsa.historystages.util.lock.BiomeEffectRegistry::rebuildFromConfig);
        bool("biomeClearEffectsOnLeave", Config.COMMON.biomeClearEffectsOnLeave);
        bool("biomeMessageEnabled", Config.COMMON.biomeMessageEnabled);
        str("biomeLockMessageFormat", Config.COMMON.biomeLockMessageFormat);
        bool("biomeLockInChat", Config.COMMON.biomeLockInChat);
        bool("biomeDamageEnabled", Config.COMMON.biomeDamageEnabled);
        dbl("biomeDamageAmount", Config.COMMON.biomeDamageAmount);
        integer("biomeDamageInterval", Config.COMMON.biomeDamageInterval);
        bool("biomeBlockRightClick", Config.COMMON.biomeBlockRightClick);
        bool("biomeBlockLeftClick", Config.COMMON.biomeBlockLeftClick);
        bool("biomeBlockProjectiles", Config.COMMON.biomeBlockProjectiles);

        // lock_messages
        str("msgDimensionUnknown", Config.COMMON.msgDimensionUnknown);
        str("msgMobUnknown", Config.COMMON.msgMobUnknown);
        str("msgItemLocked", Config.COMMON.msgItemLocked);
        str("msgBlockLocked", Config.COMMON.msgBlockLocked);
        str("msgEntityItemLocked", Config.COMMON.msgEntityItemLocked);
        str("msgEnchantmentLocked", Config.COMMON.msgEnchantmentLocked);

        // scroll_tooltip
        list("scrollTooltipLines", Config.COMMON.scrollTooltipLines, ";",
                net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout::rebuildFromConfig);
        bool("hideFulfilledDependencies", Config.COMMON.hideFulfilledDependencies);

        bool("enableScrollResealing", Config.COMMON.enableScrollResealing);

        // open_scroll
        list("openScrollChapters", Config.COMMON.openScrollChapters, ";", null);
        str("openScrollLockedDisplay", Config.COMMON.openScrollLockedDisplay);
        list("openScrollOverviewBlocks", Config.COMMON.openScrollOverviewBlocks, ";", null);
        bool("openScrollShowSearch", Config.COMMON.openScrollShowSearch);
        bool("openScrollShowEntryIds", Config.COMMON.openScrollShowEntryIds);
        str("openScrollEntrySort", Config.COMMON.openScrollEntrySort);
        str("openScrollInkHeading", Config.COMMON.openScrollInkHeading);
        str("openScrollInkBody", Config.COMMON.openScrollInkBody);
        str("openScrollInkFaint", Config.COMMON.openScrollInkFaint);

        verifyCoversEveryCommonValue();
    }

    /**
     * Reflects over {@link Config.Common} and complains about any value that was never registered
     * above. This is the guard against the original bug coming back: adding a config field and
     * wiring it into the editor, but forgetting it here, would otherwise fail silently again.
     * It logs rather than throws so a slip never crashes an existing world.
     */
    private static void verifyCoversEveryCommonValue() {
        List<String> missing = Arrays.stream(Config.Common.class.getDeclaredFields())
                .filter(field -> ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType()))
                .map(java.lang.reflect.Field::getName)
                .filter(name -> !ENTRIES.containsKey(name))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            LOGGER.error("[HistoryStages] Config values missing from CommonConfigSync — these save on the "
                    + "server but never reach clients: {}", missing);
        }
    }

    // --- registration helpers ---

    private static void add(String key, Supplier<String> read, Consumer<String> write) {
        if (ENTRIES.put(key, new Entry(read, write)) != null) {
            throw new IllegalStateException("Duplicate common config sync key: " + key);
        }
    }

    private static void bool(String key, ModConfigSpec.BooleanValue value) {
        add(key, () -> value.get().toString(), s -> value.set(Boolean.parseBoolean(s)));
    }

    private static void str(String key, ModConfigSpec.ConfigValue<String> value) {
        add(key, value::get, value::set);
    }

    private static void integer(String key, ModConfigSpec.IntValue value) {
        add(key, () -> value.get().toString(), s -> {
            try {
                value.set(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                LOGGER.warn("[HistoryStages] Ignoring malformed integer for config key '{}': {}", key, s);
            }
        });
    }

    private static void dbl(String key, ModConfigSpec.DoubleValue value) {
        add(key, () -> value.get().toString(), s -> {
            try {
                value.set(Double.parseDouble(s.trim()));
            } catch (NumberFormatException e) {
                LOGGER.warn("[HistoryStages] Ignoring malformed number for config key '{}': {}", key, s);
            }
        });
    }

    /**
     * A string list flattened with {@code separator}. {@code onApply} rebuilds whatever in-memory
     * registry parses the list, so a change takes effect without a reload; pass null if there is none.
     */
    private static void list(String key, ModConfigSpec.ConfigValue<List<? extends String>> value,
                             String separator, Consumer<List<String>> onApply) {
        add(key,
                () -> value.get().stream().map(Object::toString).collect(Collectors.joining(separator)),
                s -> {
                    List<String> parsed = Arrays.stream(s.split(separator))
                            .map(String::trim)
                            .filter(entry -> !entry.isEmpty())
                            .collect(Collectors.toList());
                    value.set(parsed);
                    if (onApply != null) onApply.accept(parsed);
                });
    }

    // --- public API ---

    /**
     * Registers a value supplied from outside this class — an addon's common config field.
     *
     * <p>Keeps the same contract as the built-in entries above: read and write are declared
     * together, so a value the editor can change is always a value that travels back to clients.
     * The wire keys are the packet's protocol, so a duplicate is not silently overwritten — two
     * addons quietly stepping on each other's values is exactly the class of silent bug this class
     * exists to prevent.
     *
     * @throws IllegalArgumentException if {@code key} is already registered
     */
    public static void register(String key, Supplier<String> read, Consumer<String> write) {
        if (ENTRIES.containsKey(key)) {
            throw new IllegalArgumentException("A common config sync value is already registered "
                    + "under wire key '" + key + "'.");
        }
        ENTRIES.put(key, new Entry(read, write));
    }

    /** Every wire key this mod syncs, in config-file order. */
    public static Set<String> keys() {
        return Collections.unmodifiableSet(ENTRIES.keySet());
    }

    /** Reads the current server-side common config into the wire map. */
    public static Map<String, String> readAll() {
        Map<String, String> values = new LinkedHashMap<>();
        ENTRIES.forEach((key, entry) -> values.put(key, entry.read().get()));
        return values;
    }

    /**
     * Writes wire values back into the common config. Unknown keys are logged rather than dropped
     * silently — a silent drop is exactly how the previous drift stayed invisible for so long.
     */
    public static void applyAll(Map<String, String> values) {
        for (Map.Entry<String, String> incoming : values.entrySet()) {
            Entry entry = ENTRIES.get(incoming.getKey());
            if (entry == null) {
                LOGGER.warn("[HistoryStages] Unknown common config key '{}' — ignored.", incoming.getKey());
                continue;
            }
            entry.write().accept(incoming.getValue());
        }
    }
}
