package net.bananemdnsa.historystages.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Core init point for optional, mod-specific lock adapters. Each adapter is loaded only when
 * its mod is present, and only ever calls the generic {@code LockGate} API — the core never
 * depends on any third-party type. Custom-action mods (e.g. spell casters) whose actions bypass
 * vanilla interaction events are handled here, isolated from the generic lock handlers.
 */
public final class LockInterceptors {

    private static final Logger LOGGER = LogUtils.getLogger();

    private LockInterceptors() {}

    public static void init() {
        if (ModList.get().isLoaded("spell_engine")) {
            try {
                net.bananemdnsa.historystages.compat.spellengine.SpellEngineCompat.register();
                LOGGER.info("[HistoryStages] Spell Engine lock adapter loaded.");
            } catch (Throwable t) {
                LOGGER.error("[HistoryStages] Failed to load Spell Engine lock adapter.", t);
            }
        }
    }
}
