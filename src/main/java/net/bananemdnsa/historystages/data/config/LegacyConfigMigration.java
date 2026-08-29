package net.bananemdnsa.historystages.data.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.Config;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time carry-over of a pre-6.0 pack's settings into {@code visual.toml} and
 * {@code gameplay.toml}.
 *
 * <p>Without this, updating from 5.x looks like the mod resetting itself: the old
 * {@code historystages-client.toml} and {@code historystages-common.toml} are no longer registered
 * with anything, so nothing reads them and every setting silently reverts to its default.
 * {@link LegacyConfigMap} says where each old setting went; this class does the file work.
 *
 * <p>Like {@link net.bananemdnsa.historystages.data.graph.GraphConfigMigration}, this runs in two
 * steps, and for the same reason. {@link #capture()} reads the raw files directly — bypassing
 * NeoForge's config objects entirely — as early as the mod constructor allows, before any spec is
 * registered, so nothing can strip or rewrite a key out from under it. Only once the specs have
 * actually loaded can anything be written into them, which is what {@link #apply()} is for.
 *
 * <p>The gap between the two steps is not optional. A {@code COMMON} spec is not loaded when
 * {@code registerConfig} returns — FML only opens {@code STARTUP} configs there and leaves the
 * rest to {@code loadConfigs(Type.COMMON, ...)} later in mod loading — and
 * {@code ModConfigSpec.ConfigValue.set} throws outright while the spec has no loaded config
 * behind it. So {@code apply()} hangs off {@code ModConfigEvent.Loading} and checks
 * {@code isLoaded()} on both specs itself rather than trusting a call site to have got the order
 * right.
 *
 * <p>The rename to {@code .migrated} happens last, and deliberately in {@code apply()} rather than
 * in {@code capture()}: {@code GraphConfigMigration.capture()} reads the same common file for its
 * own {@code [graph]} block, and renaming the file before it had its turn would cost a pack its
 * entire stage graph — {@code enabled} defaults to false, so the graph would just stop existing
 * with nothing to explain why.
 *
 * <p><strong>Delete in {@value LegacyConfigMap#REMOVE_IN}</strong>, together with
 * {@link LegacyConfigMap} — see that class for the full list.
 */
public final class LegacyConfigMigration {

    /**
     * The plain game log, not {@code DebugLogger}.
     *
     * <p>{@code DebugLogger} buffers into a report that is only written once a world loads. This
     * all happens during mod loading, and the one person who needs to read it — someone who has
     * just updated a pack and started the game to see whether it survived — may well never get
     * that far. A migration you have to load a world to hear about is not much of a migration.
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String CLIENT_FILE = "historystages-client.toml";
    private static final String COMMON_FILE = "historystages-common.toml";

    /** Suffix that marks an old file as read, so a second launch leaves the new files alone. */
    private static final String ARCHIVE_SUFFIX = ".migrated";

    /**
     * Every leaf found in the old files, keyed {@code "<client|common>|<dotted path>"}.
     *
     * <p>Null means capture found nothing worth migrating, which is the normal case for a fresh
     * install and for every launch after the first one.
     */
    private static Map<String, String> captured;

    /** Set the moment {@link #apply()} commits, so a second config load cannot run it again. */
    private static boolean applied;

    /** How many values were written, for the editor's one-off notice. */
    private static int carried;

    private LegacyConfigMigration() {}

    /**
     * Reads both old files raw. Must be called from the mod constructor, after
     * {@code GraphConfigMigration.capture()} and before any spec is registered.
     */
    public static void capture() {
        Map<String, String> values = new LinkedHashMap<>();
        read(CLIENT_FILE, LegacyConfigMap.CLIENT, values);
        read(COMMON_FILE, LegacyConfigMap.COMMON, values);
        if (!values.isEmpty()) captured = values;
    }

    private static void read(String fileName, String label, Map<String, String> out) {
        File file = FMLPaths.CONFIGDIR.get().resolve(fileName).toFile();
        if (!file.exists()) return;

        try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
            config.load();
            flatten(config, label + "|", out);
        } catch (Exception e) {
            LOGGER.error("[HistoryStages] Could not read {}", fileName, e);
        }
    }

    private static void flatten(UnmodifiableConfig config, String prefix, Map<String, String> out) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            Object raw = entry.getRawValue();
            if (raw instanceof UnmodifiableConfig nested) {
                flatten(nested, prefix + entry.getKey() + ".", out);
            } else if (raw != null) {
                out.put(prefix + entry.getKey(), encode(raw));
            }
        }
    }

    /**
     * Renders one raw TOML value the way {@link ConfigSpecCodec} expects to read it back.
     *
     * <p>The list case is the whole point. In the old file a list is a real TOML array, so
     * nightconfig hands back a {@link List}, while {@code ConfigSpecCodec} speaks in
     * {@code ";"}-joined strings. Letting a list fall through to {@code String.valueOf} would
     * produce Java's {@code [a, b]} — brackets, comma-space and all — and the codec would either
     * store that as a single nonsense entry or reject it. Nothing would error; the setting would
     * just come out wrong.
     */
    private static String encode(Object value) {
        if (!(value instanceof List<?> list)) return String.valueOf(value);

        StringBuilder joined = new StringBuilder();
        for (Object element : list) {
            if (element == null) continue;
            if (joined.length() > 0) joined.append(ConfigSpecCodec.LIST_SEPARATOR);
            joined.append(element);
        }
        return joined.toString();
    }

    /**
     * Writes whatever {@link #capture()} found into the two new specs, then puts the old files
     * beyond reach.
     *
     * <p>Safe to call on every config load: it does nothing until both specs are loaded, and
     * nothing ever again once it has run.
     */
    /**
     * How many values the migration carried over, or zero if it never ran.
     *
     * <p>Read by the config editor, which tells a pack author their settings moved. A log line
     * alone is not enough: someone who updates, launches and goes straight to the editor to look
     * for a setting never sees it.
     */
    public static int carriedCount() {
        return carried;
    }

    /** True once a migration actually moved something. */
    public static boolean migrated() {
        return carried > 0;
    }

    public static void apply() {
        if (applied || captured == null) return;
        if (!Config.VISUAL_SPEC.isLoaded() || !Config.GAMEPLAY_SPEC.isLoaded()) return;

        // Before the body, not after: saving a spec fires a Reloading event, and this must not be
        // re-entrant even if a future listener routes one of those back here.
        applied = true;

        try {
            Map<String, String> visual = new LinkedHashMap<>();
            Map<String, String> gameplay = new LinkedHashMap<>();
            List<String> homeless = new ArrayList<>();

            for (Map.Entry<String, String> entry : captured.entrySet()) {
                int split = entry.getKey().indexOf('|');
                String oldFile = entry.getKey().substring(0, split);
                String oldPath = entry.getKey().substring(split + 1);

                LegacyConfigMap.Destination destination = LegacyConfigMap.lookup(oldFile, oldPath);
                if (destination == null) {
                    // Never silently: a key the table has simply never heard of is either a
                    // hand-written typo or a mapping somebody forgot, and both want saying out
                    // loud. A key we removed on purpose has a reason and stays quiet.
                    if (LegacyConfigMap.droppedReason(oldFile, oldPath) == null) {
                        homeless.add(oldFile + ":" + oldPath);
                    }
                    continue;
                }

                Map<String, String> into =
                        destination.target() == LegacyConfigMap.Target.VISUAL ? visual : gameplay;
                into.put(destination.path(), entry.getValue());
            }

            int wrote = ConfigSpecCodec.apply(
                    Config.VISUAL_SPEC, visual, true, ConfigSpecCodec.NO_EXTRA_CHECK);
            wrote += ConfigSpecCodec.apply(
                    Config.GAMEPLAY_SPEC, gameplay, true, ConfigSpecCodec.NO_EXTRA_CHECK);

            Config.VISUAL_SPEC.save();
            Config.GAMEPLAY_SPEC.save();

            // Three of the values that just landed are lists that get parsed once into in-memory
            // structures. Without this the pack would keep its old research boosters, biome
            // effects and scroll tooltip layout until the next restart.
            ConfigDerivedCaches.rebuildAll();

            carried = wrote;
            LOGGER.info("[HistoryStages] Carried " + wrote + " of " + (visual.size() + gameplay.size())
                    + " settings from the pre-6.0 config files into visual.toml and gameplay.toml.");
            if (!homeless.isEmpty()) {
                LOGGER.warn("[HistoryStages] These old settings have no home in 6.0 and were not "
                        + "carried over: " + homeless);
            }

            archive(CLIENT_FILE);
            archive(COMMON_FILE);
        } catch (Exception e) {
            LOGGER.error("[HistoryStages] Could not migrate the pre-6.0 config files", e);
        } finally {
            captured = null;
        }
    }

    /**
     * Renames one old file out of the way so the migration cannot run a second time.
     *
     * <p>Renamed rather than deleted: if any of this turned out to be wrong, the pack's original
     * settings are still sitting right there next to the new files.
     */
    private static void archive(String fileName) {
        Path old = FMLPaths.CONFIGDIR.get().resolve(fileName);
        if (!Files.exists(old)) return;

        try {
            Files.move(old, old.resolveSibling(fileName + ARCHIVE_SUFFIX),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // Worth shouting about: the values did land, but the file staying put means the next
            // launch migrates them again over whatever has been changed since.
            LOGGER.error("[HistoryStages] Migrated the settings but could not rename {}, "
                    + "so this will run again next launch", fileName, e);
        }
    }
}
