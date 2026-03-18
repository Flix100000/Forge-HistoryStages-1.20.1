package net.bananemdnsa.historystages.util;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dedicated debug logger for History Stages.
 * Writes structured error reports to config/historystages/logs/
 * Only creates log files when there are actual issues to report.
 */
public class DebugLogger {

    private static final Path LOGS_PATH = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("logs");
    private static final int MAX_LOG_FILES = 10;
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Categorized error collection
    private static final Map<String, List<LogEntry>> CATEGORIES = new LinkedHashMap<>();
    private static int stagesLoaded = 0;

    public enum Level {
        ERROR, WARN, INFO
    }

    private record LogEntry(Level level, String message) {}

    /**
     * Clear all collected log entries. Call this before a reload cycle.
     */
    public static void clear() {
        CATEGORIES.clear();
        stagesLoaded = 0;
    }

    /**
     * Set the number of successfully loaded stages (for the summary).
     */
    public static void setStagesLoaded(int count) {
        stagesLoaded = count;
    }

    /**
     * Log an error to a specific category.
     */
    public static void log(String category, Level level, String message) {
        CATEGORIES.computeIfAbsent(category, k -> new ArrayList<>()).add(new LogEntry(level, message));
    }

    /**
     * Shorthand for ERROR level.
     */
    public static void error(String category, String message) {
        log(category, Level.ERROR, message);
    }

    /**
     * Shorthand for WARN level.
     */
    public static void warn(String category, String message) {
        log(category, Level.WARN, message);
    }

    /**
     * Shorthand for INFO level.
     */
    public static void info(String category, String message) {
        log(category, Level.INFO, message);
    }

    /**
     * Returns true if there are any logged entries.
     */
    public static boolean hasEntries() {
        return !CATEGORIES.isEmpty();
    }

    /**
     * Returns total number of entries across all categories.
     */
    public static int getTotalEntries() {
        return CATEGORIES.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Write the collected log entries to a file.
     * Only writes if there are actual issues. Cleans up old log files.
     */
    public static void writeLogFile() {
        if (CATEGORIES.isEmpty()) return;

        try {
            File logsDir = LOGS_PATH.toFile();
            if (!logsDir.exists()) logsDir.mkdirs();

            // Clean up old log files (keep only MAX_LOG_FILES)
            cleanupOldLogs(logsDir);

            // Create new log file
            LocalDateTime now = LocalDateTime.now();
            String fileName = "debug-" + now.format(FILE_FORMAT) + ".log";
            File logFile = new File(logsDir, fileName);

            // Count totals
            int errorCount = 0;
            int warnCount = 0;
            int infoCount = 0;
            for (List<LogEntry> entries : CATEGORIES.values()) {
                for (LogEntry entry : entries) {
                    switch (entry.level) {
                        case ERROR -> errorCount++;
                        case WARN -> warnCount++;
                        case INFO -> infoCount++;
                    }
                }
            }

            // Get mod version
            String modVersion = ModList.get().getModContainerById("historystages")
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");

            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile))) {
                // Header
                pw.println("================================================================");
                pw.println("  History Stages - Debug Report");
                pw.println("  Generated: " + now.format(DISPLAY_FORMAT));
                pw.println("  Mod Version: " + modVersion);
                pw.println("================================================================");
                pw.println();
                pw.println("  Stages loaded: " + stagesLoaded);
                pw.println("  Total issues:  " + getTotalEntries());
                pw.println("  Errors: " + errorCount + "  |  Warnings: " + warnCount + "  |  Info: " + infoCount);
                pw.println();

                // Categories
                for (Map.Entry<String, List<LogEntry>> category : CATEGORIES.entrySet()) {
                    pw.println("--- " + category.getKey() + " " + "-".repeat(Math.max(0, 60 - category.getKey().length() - 5)));
                    pw.println();

                    for (LogEntry entry : category.getValue()) {
                        String prefix = switch (entry.level) {
                            case ERROR -> "[ERROR] ";
                            case WARN ->  "[WARN]  ";
                            case INFO ->  "[INFO]  ";
                        };
                        pw.println("  " + prefix + entry.message);
                    }
                    pw.println();
                }

                // Footer
                pw.println("================================================================");
                pw.println("  This file was auto-generated by History Stages.");
                pw.println("  Only errors and warnings are logged here.");
                pw.println("  Location: config/historystages/logs/");
                pw.println("  To disable chat debug messages: set showDebugErrors=false");
                pw.println("  in historystages-common.toml");
                pw.println("================================================================");
            }

            System.out.println("[HistoryStages] Debug report written to: logs/" + fileName);

        } catch (Exception e) {
            System.err.println("[HistoryStages] Failed to write debug log: " + e.getMessage());
        }
    }

    /**
     * Remove old log files, keeping only the newest MAX_LOG_FILES.
     */
    private static void cleanupOldLogs(File logsDir) {
        File[] logFiles = logsDir.listFiles((dir, name) -> name.startsWith("debug-") && name.endsWith(".log"));
        if (logFiles == null || logFiles.length < MAX_LOG_FILES) return;

        Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

        int toDelete = logFiles.length - MAX_LOG_FILES + 1; // +1 to make room for the new file
        for (int i = 0; i < toDelete; i++) {
            logFiles[i].delete();
        }
    }
}
