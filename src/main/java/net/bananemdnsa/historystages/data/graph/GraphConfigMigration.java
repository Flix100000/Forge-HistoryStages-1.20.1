package net.bananemdnsa.historystages.data.graph;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.util.Locale;

/**
 * One-time carry-over of the old {@code [graph]} block from
 * {@code historystages-common.toml} into {@code graph.toml}.
 *
 * <p>Without this, updating would silently switch every pack's graph off: {@code enabled}
 * defaults to false, so a pack author who turned it on would find it off again with no hint why.
 *
 * <p>This runs in two steps because the old file does not survive startup. Originally the threat
 * was NeoForge itself: it corrects a config file while loading it and writes the corrected version
 * straight back, so loading the old common spec stripped the now-unknown {@code [graph]} keys
 * before any {@code ModConfigEvent} fired. Since 6.0.0 nothing registers
 * {@code historystages-common.toml} at all, but the deadline is if anything tighter —
 * {@link net.bananemdnsa.historystages.data.config.LegacyConfigMigration} renames that file out of
 * the way once it has taken its own copy.
 *
 * <p>So {@link #capture()} reads the raw file directly, bypassing NeoForge's config objects, as the
 * very first thing the mod constructor does — ahead of the general migration's capture, and long
 * before either spec is registered. {@link #apply()} then writes the captured values into
 * {@code graph.toml} once that spec has actually loaded, so our writes are not immediately
 * overwritten by its own file load.
 */
public final class GraphConfigMigration {

    private GraphConfigMigration() {}

    /**
     * Raw values pulled from the old {@code [graph]} block before it can be stripped.
     *
     * <p>Every field is nullable, and null means "the old file did not have this key". That
     * distinction matters: three of these default to {@code true} in the new spec, so treating a
     * missing key as {@code false} would silently switch features off for anyone whose old block
     * was incomplete — including a hand-written partial block.
     */
    private record Captured(Boolean enabled, Boolean respectHiddenDisplay,
                            Boolean showIndividualStages, Boolean showStageElements,
                            Boolean showTriggers, String visibility) {}

    private static Captured captured;

    /**
     * Reads the old {@code [graph]} block, if present, before NeoForge's own config loading has
     * a chance to strip it. Must be called before {@code registerConfig} for either spec.
     */
    public static void capture() {
        File old = FMLPaths.CONFIGDIR.get().resolve("historystages-common.toml").toFile();
        if (!old.exists()) return;

        try (CommentedFileConfig cfg = CommentedFileConfig.builder(old).sync().build()) {
            cfg.load();
            if (cfg.get("graph") == null) return;

            captured = new Captured(
                    cfg.get("graph.enabled"),
                    cfg.get("graph.respectHiddenDisplay"),
                    cfg.get("graph.showIndividualStages"),
                    cfg.get("graph.showStageElements"),
                    cfg.get("graph.showTriggers"),
                    cfg.get("graph.visibility"));
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not read the old [graph] block: " + e.getMessage());
        }
    }

    /** Writes whatever {@link #capture()} found into {@code graph.toml}. No-op if nothing was captured. */
    public static void apply() {
        if (captured == null) return;

        try {
            if (captured.enabled() != null) GraphConfig.GRAPH.enabled.set(captured.enabled());
            if (captured.respectHiddenDisplay() != null) {
                GraphConfig.GRAPH.respectHiddenDisplay.set(captured.respectHiddenDisplay());
            }
            if (captured.showIndividualStages() != null) {
                GraphConfig.GRAPH.showIndividualStages.set(captured.showIndividualStages());
            }
            // showStageElements gated every non-stage detail node at once. Its closest equivalent
            // now is the group of panel toggles, so it is spread across them.
            if (captured.showStageElements() != null) {
                boolean elements = captured.showStageElements();
                GraphConfig.GRAPH.showItems.set(elements);
                GraphConfig.GRAPH.showXp.set(elements);
                GraphConfig.GRAPH.showAdvancements.set(elements);
                GraphConfig.GRAPH.showKills.set(elements);
                GraphConfig.GRAPH.showStats.set(elements);
                GraphConfig.GRAPH.showScoreboard.set(elements);
            }
            if (captured.showTriggers() != null) {
                GraphConfig.GRAPH.showTriggers.set(captured.showTriggers());
            }

            if (captured.visibility() != null) {
                try {
                    GraphConfig.GRAPH.visibilityMode.set(GraphConfig.GraphVisibility.valueOf(
                            captured.visibility().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Hand-edited to something unknown; the default stands.
                }
            }

            GraphConfig.GRAPH_SPEC.save();
            DebugLogger.info("Stage Graph",
                    "Migrated the old [graph] settings from historystages-common.toml.");
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not migrate the old [graph] block: " + e.getMessage());
        } finally {
            // One-time: whether it succeeded or failed, do not try again this session.
            captured = null;
        }
    }
}
