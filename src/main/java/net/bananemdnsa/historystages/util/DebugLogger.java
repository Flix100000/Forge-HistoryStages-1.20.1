package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.EntityLocks;
import net.bananemdnsa.historystages.data.StageEntry;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Dedicated debug logger for History Stages.
 * <p>
 * Two modes:
 * <ul>
 *   <li><b>Load-time report</b> — categorized issues written to debug-*.log after stage loading</li>
 *   <li><b>Runtime log</b> — timestamped event log appended to runtime-YYYY-MM-DD.log, buffered and flushed periodically</li>
 * </ul>
 */
public class DebugLogger {

    private static final Path LOGS_PATH = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("logs");
    private static final int MAX_LOG_FILES = 10;
    private static final int MAX_RUNTIME_FILES = 7;
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ==================== Load-time report ====================

    private static final Map<String, List<LoadEntry>> CATEGORIES = new LinkedHashMap<>();
    private static int stagesLoaded = 0;

    public enum Level {
        ERROR, WARN, INFO
    }

    private record LoadEntry(Level level, String message) {}

    public static void clear() {
        CATEGORIES.clear();
        stagesLoaded = 0;
    }

    public static void setStagesLoaded(int count) {
        stagesLoaded = count;
    }

    public static void log(String category, Level level, String message) {
        CATEGORIES.computeIfAbsent(category, k -> new ArrayList<>()).add(new LoadEntry(level, message));
    }

    public static void error(String category, String message) {
        log(category, Level.ERROR, message);
    }

    public static void warn(String category, String message) {
        log(category, Level.WARN, message);
    }

    public static void info(String category, String message) {
        log(category, Level.INFO, message);
    }

    public static boolean hasEntries() {
        return !CATEGORIES.isEmpty();
    }

    public static int getTotalEntries() {
        return CATEGORIES.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Write a comprehensive diagnostic report.
     * Only creates a file when there are issues (errors/warnings/info).
     * Includes: issues, successfully loaded stages with full contents, config snapshot, and summary.
     *
     * @param stages the loaded global stage map (pass StageManager.getStages())
     * @param individualStages the loaded individual stage map (pass StageManager.getIndividualStages())
     */
    public static void writeLogFile(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages) {
        if (CATEGORIES.isEmpty()) return;

        try {
            File logsDir = LOGS_PATH.toFile();
            if (!logsDir.exists()) logsDir.mkdirs();

            cleanupOldLogs(logsDir, "debug-", MAX_LOG_FILES);

            LocalDateTime now = LocalDateTime.now();
            String fileName = "debug-" + now.format(FILE_FORMAT) + ".log";
            File logFile = new File(logsDir, fileName);

            int errorCount = 0;
            int warnCount = 0;
            int infoCount = 0;
            for (List<LoadEntry> entries : CATEGORIES.values()) {
                for (LoadEntry entry : entries) {
                    switch (entry.level) {
                        case ERROR -> errorCount++;
                        case WARN -> warnCount++;
                        case INFO -> infoCount++;
                    }
                }
            }

            String modVersion = ModList.get().getModContainerById("historystages")
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");

            // Count totals across all stages
            int totalItems = 0, totalTags = 0, totalMods = 0, totalRecipes = 0;
            int totalDimensions = 0, totalAttacklock = 0, totalSpawnlock = 0;
            for (StageEntry entry : stages.values()) {
                totalItems += entry.getItems().size();
                totalTags += entry.getTags().size();
                totalMods += entry.getMods().size();
                totalRecipes += entry.getRecipes().size();
                totalDimensions += entry.getDimensions().size();
                totalAttacklock += entry.getEntities().getAttacklock().size();
                totalSpawnlock += entry.getEntities().getSpawnlock().size();
            }

            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile))) {

                // ── Header ──
                pw.println("================================================================");
                pw.println("  History Stages - Diagnostic Report");
                pw.println("  Generated: " + now.format(DISPLAY_FORMAT));
                pw.println("  Mod Version: " + modVersion);
                pw.println("  Minecraft Forge: " + getForgeVersion());
                pw.println("================================================================");
                pw.println();

                // ── Summary ──
                pw.println("  Global stages loaded:     " + stagesLoaded);
                pw.println("  Individual stages loaded: " + (individualStages != null ? individualStages.size() : 0));
                pw.println("  Total issues:             " + getTotalEntries()
                        + "  (Errors: " + errorCount + "  |  Warnings: " + warnCount + "  |  Info: " + infoCount + ")");
                pw.println();
                pw.println("  Total entries across global stages:");
                pw.println("    Items: " + totalItems + "  |  Tags: " + totalTags + "  |  Mods: " + totalMods);
                pw.println("    Recipes: " + totalRecipes + "  |  Dimensions: " + totalDimensions);
                pw.println("    Entities (attacklock): " + totalAttacklock + "  |  Entities (spawnlock): " + totalSpawnlock);
                pw.println();

                // ── Issues ──
                pw.println("================================================================");
                pw.println("  ISSUES");
                pw.println("================================================================");
                pw.println();

                for (Map.Entry<String, List<LoadEntry>> category : CATEGORIES.entrySet()) {
                    pw.println("--- " + category.getKey() + " " + "-".repeat(Math.max(0, 60 - category.getKey().length() - 5)));
                    pw.println();
                    for (LoadEntry entry : category.getValue()) {
                        String prefix = switch (entry.level) {
                            case ERROR -> "[ERROR] ";
                            case WARN ->  "[WARN]  ";
                            case INFO ->  "[INFO]  ";
                        };
                        pw.println("  " + prefix + entry.message);
                    }
                    pw.println();
                }

                // ── Loaded Global Stages ──
                pw.println("================================================================");
                pw.println("  LOADED GLOBAL STAGES (" + stagesLoaded + ")");
                pw.println("================================================================");
                pw.println();

                for (Map.Entry<String, StageEntry> stageEntry : stages.entrySet()) {
                    String id = stageEntry.getKey();
                    StageEntry s = stageEntry.getValue();
                    EntityLocks ent = s.getEntities();

                    int entryCount = s.getItems().size() + s.getTags().size() + s.getMods().size()
                            + s.getRecipes().size() + s.getDimensions().size()
                            + ent.getAttacklock().size() + ent.getSpawnlock().size();

                    pw.println("--- " + id + " (" + s.getDisplayName() + ") " + "-".repeat(Math.max(0, 50 - id.length() - s.getDisplayName().length())));
                    pw.println("  Research time: " + (s.getResearchTime() > 0 ? s.getResearchTime() + "s (custom)" : "global default"));
                    pw.println("  Total entries: " + entryCount);

                    printList(pw, "Items", s.getItems());
                    printList(pw, "Tags", s.getTags());
                    printList(pw, "Mods", s.getMods());
                    printList(pw, "Recipes", s.getRecipes());
                    printList(pw, "Dimensions", s.getDimensions());
                    printList(pw, "Entities (attacklock)", ent.getAttacklock());
                    printList(pw, "Entities (spawnlock)", ent.getSpawnlock());

                    pw.println();
                }

                // ── Loaded Individual Stages ──
                if (individualStages != null && !individualStages.isEmpty()) {
                    pw.println("================================================================");
                    pw.println("  LOADED INDIVIDUAL STAGES (" + individualStages.size() + ")");
                    pw.println("================================================================");
                    pw.println();

                    for (Map.Entry<String, StageEntry> stageEntry : individualStages.entrySet()) {
                        String id = stageEntry.getKey();
                        StageEntry s = stageEntry.getValue();
                        EntityLocks ent = s.getEntities();

                        int entryCount = s.getItems().size() + s.getTags().size() + s.getMods().size()
                                + s.getRecipes().size() + s.getDimensions().size()
                                + ent.getAttacklock().size() + ent.getSpawnlock().size();

                        pw.println("--- " + id + " (" + s.getDisplayName() + ") " + "-".repeat(Math.max(0, 50 - id.length() - s.getDisplayName().length())));
                        pw.println("  Research time: " + (s.getResearchTime() > 0 ? s.getResearchTime() + "s (custom)" : "global default"));
                        pw.println("  Total entries: " + entryCount);

                        printList(pw, "Items", s.getItems());
                        printList(pw, "Tags", s.getTags());
                        printList(pw, "Mods", s.getMods());
                        printList(pw, "Recipes", s.getRecipes());
                        printList(pw, "Dimensions", s.getDimensions());
                        printList(pw, "Entities (attacklock)", ent.getAttacklock());
                        printList(pw, "Entities (spawnlock)", ent.getSpawnlock());

                        pw.println();
                    }
                }

                // ── Config Snapshot ──
                pw.println("================================================================");
                pw.println("  CONFIG SNAPSHOT");
                pw.println("================================================================");
                pw.println();
                try {
                    pw.println("  [common]");
                    pw.println("    lockMobLoot          = " + Config.COMMON.lockMobLoot.get());
                    pw.println("    lockBlockBreaking    = " + Config.COMMON.lockBlockBreaking.get());
                    pw.println("    lockBlockBreakSpeed  = " + Config.COMMON.lockedBlockBreakSpeedMultiplier.get());
                    pw.println("    lockItemUsage        = " + Config.COMMON.lockItemUsage.get());
                    pw.println("    lockEntityItems      = " + Config.COMMON.lockEntityItems.get());
                    pw.println("    lockBlockInteraction = " + Config.COMMON.lockBlockInteraction.get());
                    pw.println("    researchTimeSeconds  = " + Config.COMMON.researchTimeInSeconds.get());
                    pw.println("    useReplacements      = " + Config.COMMON.useReplacements.get());
                    pw.println("    broadcastChat        = " + Config.COMMON.broadcastChat.get());
                    pw.println("    useSounds            = " + Config.COMMON.useSounds.get());
                    pw.println("    useToasts            = " + Config.COMMON.useToasts.get());
                    pw.println("    showDebugErrors      = " + Config.COMMON.showDebugErrors.get());
                    pw.println("    enableRuntimeLogging = " + Config.COMMON.enableRuntimeLogging.get());
                } catch (Exception e) {
                    pw.println("  (Config not yet available: " + e.getMessage() + ")");
                }
                pw.println();

                // ── Footer ──
                pw.println("================================================================");
                pw.println("  This file was auto-generated by History Stages.");
                pw.println("  It is created when issues are found during stage loading.");
                pw.println("  Share this file when reporting bugs.");
                pw.println();
                pw.println("  Global stages:     config/historystages/global/");
                pw.println("  Individual stages: config/historystages/individual/");
                pw.println("  Log files:         config/historystages/logs/");
                pw.println("  Disable chat debug messages: showDebugErrors=false");
                pw.println("  in historystages-common.toml");
                pw.println("================================================================");
            }

            System.out.println("[HistoryStages] Diagnostic report written to: logs/" + fileName);

        } catch (Exception e) {
            System.err.println("[HistoryStages] Failed to write debug log: " + e.getMessage());
        }
    }

    private static void printList(PrintWriter pw, String label, List<String> list) {
        if (list == null || list.isEmpty()) return;
        pw.println("  " + label + " (" + list.size() + "):");
        for (String entry : list) {
            pw.println("    - " + entry);
        }
    }

    private static String getForgeVersion() {
        try {
            return ModList.get().getModContainerById("forge")
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ==================== Runtime logging ====================

    private static final ConcurrentLinkedQueue<String> RUNTIME_BUFFER = new ConcurrentLinkedQueue<>();
    private static final Map<String, Long> THROTTLE_MAP = new HashMap<>();
    private static final long THROTTLE_MS = 5000; // 5s throttle for high-frequency events
    private static volatile String runtimeFileName = null;
    private static volatile boolean headerWritten = false;

    /**
     * Log a runtime event. Thread-safe, buffered.
     * Will only be written to file when flush() is called.
     */
    public static void runtime(String category, String message) {
        if (!isRuntimeEnabled()) return;
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        RUNTIME_BUFFER.add("[" + timestamp + "] [" + category + "] " + message);
    }

    /**
     * Log a runtime event with player context.
     */
    public static void runtime(String category, String playerName, String message) {
        if (!isRuntimeEnabled()) return;
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        RUNTIME_BUFFER.add("[" + timestamp + "] [" + category + "] <" + playerName + "> " + message);
    }

    /**
     * Log a throttled runtime event. Events with the same throttleKey
     * are suppressed for THROTTLE_MS after the first occurrence.
     * Use this for high-frequency events like mob spawn blocks.
     */
    public static void runtimeThrottled(String category, String throttleKey, String message) {
        if (!isRuntimeEnabled()) return;
        long now = System.currentTimeMillis();
        synchronized (THROTTLE_MAP) {
            Long last = THROTTLE_MAP.get(throttleKey);
            if (last != null && (now - last) < THROTTLE_MS) return;
            THROTTLE_MAP.put(throttleKey, now);
        }
        runtime(category, message);
    }

    /**
     * Initialize a new runtime log file for this server session.
     * Call this once when the server/world starts.
     */
    public static void initRuntimeSession() {
        runtimeFileName = "runtime-" + LocalDateTime.now().format(FILE_FORMAT) + ".log";
        headerWritten = false;

        File logsDir = LOGS_PATH.toFile();
        if (logsDir.exists()) {
            cleanupOldLogs(logsDir, "runtime-", MAX_RUNTIME_FILES);
        }
    }

    /**
     * Flush the runtime buffer to the session log file.
     * Call this periodically from a server tick handler.
     */
    public static void flushRuntimeBuffer() {
        if (RUNTIME_BUFFER.isEmpty()) return;
        if (runtimeFileName == null) initRuntimeSession();

        try {
            File logsDir = LOGS_PATH.toFile();
            if (!logsDir.exists()) logsDir.mkdirs();

            File runtimeFile = new File(logsDir, runtimeFileName);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(runtimeFile, true))) {
                if (!headerWritten) {
                    headerWritten = true;

                    String modVersion = ModList.get().getModContainerById("historystages")
                            .map(c -> c.getModInfo().getVersion().toString())
                            .orElse("unknown");

                    bw.write("================================================================");
                    bw.newLine();
                    bw.write("  History Stages - Runtime Log");
                    bw.newLine();
                    bw.write("  Session started: " + LocalDateTime.now().format(DISPLAY_FORMAT));
                    bw.newLine();
                    bw.write("  Mod Version: " + modVersion);
                    bw.newLine();
                    bw.write("================================================================");
                    bw.newLine();
                    bw.newLine();
                }

                String entry;
                while ((entry = RUNTIME_BUFFER.poll()) != null) {
                    bw.write(entry);
                    bw.newLine();
                }
            }

        } catch (Exception e) {
            System.err.println("[HistoryStages] Failed to flush runtime log: " + e.getMessage());
        }
    }

    /**
     * Clean up old throttle entries to prevent memory leaks.
     * Call periodically (e.g., every few minutes).
     */
    public static void cleanupThrottleMap() {
        long now = System.currentTimeMillis();
        synchronized (THROTTLE_MAP) {
            THROTTLE_MAP.entrySet().removeIf(e -> (now - e.getValue()) > THROTTLE_MS * 2);
        }
    }

    private static boolean isRuntimeEnabled() {
        try {
            return Config.COMMON.enableRuntimeLogging.get();
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Shared utilities ====================

    private static void cleanupOldLogs(File logsDir, String prefix, int maxFiles) {
        File[] logFiles = logsDir.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".log"));
        if (logFiles == null || logFiles.length < maxFiles) return;

        Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

        int toDelete = logFiles.length - maxFiles + 1;
        for (int i = 0; i < toDelete; i++) {
            logFiles[i].delete();
        }
    }
}
