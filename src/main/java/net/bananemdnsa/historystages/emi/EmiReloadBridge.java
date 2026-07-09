package net.bananemdnsa.historystages.emi;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Best-effort bridge to trigger an EMI reload at runtime so newly-added boosters and
 * other config changes show up immediately in EMI.
 *
 * <p>EMI rebuilds its recipe/stack index only while a plugin's {@code register} runs,
 * which happens on an EMI reload. The reload entry point
 * ({@code dev.emi.emi.runtime.EmiReloadManager#reload}) lives in an internal package
 * that is not on our compile classpath, so it is invoked reflectively and fully guarded
 * — this is a no-op when EMI is absent or the method ever moves. This class intentionally
 * references no EMI type directly, so it is safe to load even without EMI installed.
 */
public final class EmiReloadBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmiReloadBridge() {}

    /** Triggers a full EMI reload if EMI is present; silently does nothing otherwise. */
    public static void reloadIfPresent() {
        try {
            Class<?> manager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
            Method reload = manager.getMethod("reload");
            reload.invoke(null);
            LOGGER.debug("[HistoryStages/EMI] Triggered EMI reload.");
        } catch (ClassNotFoundException e) {
            // EMI not installed — nothing to do.
        } catch (Throwable t) {
            LOGGER.warn("[HistoryStages/EMI] Failed to trigger EMI reload", t);
        }
    }
}
