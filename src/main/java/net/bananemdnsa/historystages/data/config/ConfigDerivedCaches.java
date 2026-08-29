package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.util.lock.BiomeEffectRegistry;

/**
 * Everything that has to be rebuilt after config values change underneath it.
 *
 * <p>Three settings are lists of text that get parsed once into an in-memory structure. Whoever
 * writes new values into a spec has to say so, or the parsed copy keeps answering with the old
 * ones — the "works only after a restart" shape of bug, which reads as timing and is really a
 * cache.
 *
 * <p>Single-sourced here because the write paths keep multiplying: an admin saving, a sync
 * arriving, and a player leaving a server all replace spec values, and the last of those was
 * already forgotten once. A fourth path will be added eventually; it should have exactly one
 * place to call.
 */
public final class ConfigDerivedCaches {

    private ConfigDerivedCaches() {}

    /** Rebuilds what the gameplay spec feeds. */
    public static void rebuildGameplay() {
        ResearchBoosterRegistry.rebuildFromConfig(Config.GAMEPLAY.researchBoosters.get());
        BiomeEffectRegistry.rebuildFromConfig(Config.GAMEPLAY.biomeEffects.get());
    }

    /** Rebuilds what the visual spec feeds. */
    public static void rebuildVisual() {
        ScrollTooltipLayout.rebuildFromConfig(Config.VISUAL.scrollTooltipLines.get());
    }

    /**
     * Rebuilds both. Deliberately unconditional: a mapping from key to rebuild would be one more
     * hand-written table of the kind this refactor removed, and rebuilding costs nothing.
     */
    public static void rebuildAll() {
        rebuildGameplay();
        rebuildVisual();
    }
}
