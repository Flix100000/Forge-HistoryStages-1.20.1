package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.HistoryStagesFabric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugLogger {
    private static final Map<String, Long> THROTTLE = new ConcurrentHashMap<>();

    private DebugLogger() {
    }

    public static void clear() {
        THROTTLE.clear();
    }

    public static void info(String category, String message) {
        HistoryStagesFabric.LOGGER.info("[{}] {}", category, message);
    }

    public static void warn(String category, String message) {
        HistoryStagesFabric.LOGGER.warn("[{}] {}", category, message);
    }

    public static void error(String category, String message) {
        HistoryStagesFabric.LOGGER.error("[{}] {}", category, message);
    }

    public static void runtime(String category, String actor, String message) {
        HistoryStagesFabric.LOGGER.info("[{}] {}: {}", category, actor, message);
    }

    public static void runtimeThrottled(String category, String key, String message) {
        long now = System.currentTimeMillis();
        Long last = THROTTLE.put(key, now);
        if (last == null || now - last > 30_000L) {
            HistoryStagesFabric.LOGGER.info("[{}] {}", category, message);
        }
    }

    public static void cleanupThrottleMap() {
        long cutoff = System.currentTimeMillis() - 300_000L;
        THROTTLE.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    public static void flushRuntimeBuffer() {
    }

    public static void setStagesLoaded(int count) {
        HistoryStagesFabric.LOGGER.info("Loaded {} stage definition(s).", count);
    }
}
