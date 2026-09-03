package net.bananemdnsa.historystages.util;

import com.google.gson.Gson;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.lock.EntityLocks;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.EntityKillDep;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.dependency.ScoreboardDep;
import net.bananemdnsa.historystages.data.dependency.StatDep;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

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
 * Two modes:
 * - Load-time report: categorized issues written to debug-*.log after stage loading
 * - Runtime log: timestamped event log appended to runtime-YYYY-MM-DD.log, buffered and flushed periodically
 */
public class DebugLogger {

    // Resolved lazily (not a static final field) so that classes calling error()/warn()/info() —
    // which only touch the in-memory CATEGORIES map — can be loaded without FMLPaths on the
    // classpath. That keeps pure/testable code (e.g. GraphLayoutData.fromJson) safe to unit-test
    // without a running game.
    private static Path logsPath() {
        return FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("logs");
    }
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

    public static void writeLogFile(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages) {
        if (CATEGORIES.isEmpty()) return;

        try {
            File logsDir = logsPath().toFile();
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

            // Counted through the category registry rather than one accumulator per kind. The
            // hand-written version listed ten of the eleven built-ins — interaction locks never
            // made it in — and could not have counted an addon's category at all, which is the
            // opposite of what a report about what got loaded is for.
            Map<String, Integer> categoryTotals = new LinkedHashMap<>();
            for (LockCategory<?> category : LockCategories.all()) {
                int total = 0;
                for (StageEntry entry : stages.values()) {
                    total += category.read(entry).size();
                }
                categoryTotals.put(category.id(), total);
            }

            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile))) {
                pw.println("================================================================");
                pw.println("  History Stages - Diagnostic Report");
                pw.println("  Generated: " + now.format(DISPLAY_FORMAT));
                pw.println("  Mod Version: " + modVersion);
                pw.println("  Minecraft NeoForge: " + getNeoForgeVersion());
                pw.println("================================================================");
                pw.println();

                pw.println("  Global stages loaded:     " + stagesLoaded);
                pw.println("  Individual stages loaded: " + (individualStages != null ? individualStages.size() : 0));
                pw.println("  Total issues:             " + getTotalEntries()
                        + "  (Errors: " + errorCount + "  |  Warnings: " + warnCount + "  |  Info: " + infoCount + ")");
                pw.println();
                pw.println("  Total entries across global stages:");
                for (Map.Entry<String, Integer> total : categoryTotals.entrySet()) {
                    pw.println("    " + total.getKey() + ": " + total.getValue());
                }
                pw.println();

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
                            case WARN -> "[WARN]  ";
                            case INFO -> "[INFO]  ";
                        };
                        pw.println("  " + prefix + entry.message);
                    }
                    pw.println();
                }

                pw.println("================================================================");
                pw.println("  LOADED GLOBAL STAGES (" + stagesLoaded + ")");
                pw.println("================================================================");
                pw.println();

                for (Map.Entry<String, StageEntry> stageEntry : stages.entrySet()) {
                    printStage(pw, stageEntry.getKey(), stageEntry.getValue());
                }

                if (individualStages != null && !individualStages.isEmpty()) {
                    pw.println("================================================================");
                    pw.println("  LOADED INDIVIDUAL STAGES (" + individualStages.size() + ")");
                    pw.println("================================================================");
                    pw.println();

                    for (Map.Entry<String, StageEntry> stageEntry : individualStages.entrySet()) {
                        printStage(pw, stageEntry.getKey(), stageEntry.getValue());
                    }
                }

                pw.println("================================================================");
                pw.println("  CONFIG SNAPSHOT");
                pw.println("================================================================");
                pw.println();
                try {
                    pw.println("  [common]");
                    pw.println("    lockMobLoot          = " + Config.GAMEPLAY.lockMobLoot.get());
                    pw.println("    lockBlockBreaking    = " + Config.GAMEPLAY.lockBlockBreaking.get());
                    pw.println("    lockBlockBreakSpeed  = " + Config.GAMEPLAY.lockedBlockBreakSpeedMultiplier.get());
                    pw.println("    lockItemUsage        = " + Config.GAMEPLAY.lockItemUsage.get());
                    pw.println("    lockEntityItems      = " + Config.GAMEPLAY.lockEntityItems.get());
                    pw.println("    lockBlockInteraction = " + Config.GAMEPLAY.lockBlockInteraction.get());
                    pw.println("    researchTimeSeconds  = " + Config.GAMEPLAY.researchTimeInSeconds.get());
                    pw.println("    useReplacements      = " + Config.GAMEPLAY.useReplacements.get());
                    pw.println("    broadcastChat        = " + Config.VISUAL.broadcastChat.get());
                    pw.println("    useSounds            = " + Config.VISUAL.useSounds.get());
                    pw.println("    useToasts            = " + Config.VISUAL.useToasts.get());
                    pw.println("    showDebugErrors      = " + Config.GAMEPLAY.showDebugErrors.get());
                    pw.println("    enableRuntimeLogging = " + Config.GAMEPLAY.enableRuntimeLogging.get());
                } catch (Exception e) {
                    pw.println("  (Config not yet available: " + e.getMessage() + ")");
                }
                pw.println();

                pw.println("================================================================");
                pw.println("  This file was auto-generated by History Stages.");
                pw.println("  It is created when issues are found during stage loading.");
                pw.println("  Share this file when reporting bugs.");
                pw.println();
                pw.println("  Global stages:     config/historystages/global/");
                pw.println("  Individual stages: config/historystages/individual/");
                pw.println("  Log files:         config/historystages/logs/");
                pw.println("  Disable chat debug messages: showDebugErrors=false");
                pw.println("  in config/historystages/settings/gameplay.toml");
                pw.println("================================================================");
            }

            System.out.println("[HistoryStages] Diagnostic report written to: logs/" + fileName);

        } catch (Exception e) {
            System.err.println("[HistoryStages] Failed to write debug log: " + e.getMessage());
        }
    }

    /**
     * The categories {@link #printStage} spells out itself, each in its own shape.
     *
     * <p>A skip list, not a whitelist: a category missing from it still gets printed, only
     * plainly. That is the safe direction — the interaction locks this report has always left
     * out, and every category an addon registers, land in {@link #printOtherCategories} rather
     * than nowhere.
     */
    private static final List<String> DETAILED_CATEGORY_IDS = List.of(
            "historystages:items", "historystages:tags", "historystages:mods",
            "historystages:mod_exceptions", "historystages:recipes", "historystages:dimensions",
            "historystages:structures", "historystages:biomes", "historystages:attacklock",
            "historystages:spawnlock");

    /** Entries are printed as JSON: it is the only shape that fits a type this class never saw. */
    private static final Gson ENTRY_GSON = new Gson();

    private static void printOtherCategories(PrintWriter pw, StageEntry s) {
        for (LockCategory<?> category : LockCategories.all()) {
            if (DETAILED_CATEGORY_IDS.contains(category.id())) continue;
            List<?> entries = category.read(s);
            if (entries.isEmpty()) continue;
            pw.println("  " + category.id() + " (" + entries.size() + "):");
            for (Object entry : entries) {
                pw.println("    - " + (entry instanceof String text ? text : ENTRY_GSON.toJson(entry)));
            }
        }
    }

    private static void printStage(PrintWriter pw, String id, StageEntry s) {
        EntityLocks ent = s.getEntities();
        List<String> modExceptions = s.getAllModExceptionIds();

        // Summed over the registry, so the number matches what the stage actually holds — the
        // hand-written sum left out interaction locks and could never have seen an addon's
        // category.
        int entryCount = 0;
        for (LockCategory<?> category : LockCategories.all()) {
            entryCount += category.read(s).size();
        }

        pw.println("--- " + id + " (" + s.getDisplayName() + ") " + "-".repeat(Math.max(0, 50 - id.length() - s.getDisplayName().length())));
        StageMode mode = s.getMode();
        String rawMode = s.getRawMode();
        if (rawMode != null && !StageMode.isKnown(rawMode)) {
            pw.println("  Mode: " + mode.serialize() + " (raw=\"" + rawMode + "\" → unknown, defaulted)");
        } else {
            pw.println("  Mode: " + mode.serialize());
        }
        pw.println("  Research time: " + (s.getResearchTime() > 0 ? s.getResearchTime() + "s (custom)" : "global default"));
        if (!s.getIcon().isEmpty()) pw.println("  Icon: " + s.getIcon());
        if (s.isLoseOnDeath()) pw.println("  Lose on death: yes");
        pw.println("  Total entries: " + entryCount);

        printItemEntries(pw, "Items", s.getItemEntries());
        printList(pw, "Fluids", s.getAllFluidIds());
        printNamedLockEntries(pw, "Tags", s.getTagEntries());
        printNamedLockEntries(pw, "Mods", s.getModEntries());
        if (!modExceptions.isEmpty()) printList(pw, "Mod Exceptions", modExceptions);
        printList(pw, "Recipes", s.getRecipes());
        printList(pw, "Dimensions", s.getDimensions());

        if (!s.getStructures().isEmpty()) {
            pw.println("  Structures (" + s.getStructures().size() + "):");
            for (String struct : s.getStructures()) {
                pw.println("    - " + struct);
            }
            if (!s.getStructureModLinked().isEmpty()) {
                pw.println("  Structures (mod-linked) (" + s.getStructureModLinked().size() + "):");
                for (String mod : s.getStructureModLinked()) {
                    pw.println("    - " + mod);
                }
            }
            if (!s.getStructureGenerationRules().isEmpty()) {
                pw.println("  Structures (generation restricted) (" + s.getStructureGenerationRules().size() + "):");
                for (net.bananemdnsa.historystages.data.lock.StructureGenerationRule rule : s.getStructureGenerationRules()) {
                    pw.println("    - " + rule.id() + " [" + rule.phase().serialize() + ", max " + rule.max()
                            + (rule.resetOnRelock() ? ", reset" : "") + "]");
                }
            }
        }

        if (!s.getBiomes().isEmpty()) {
            pw.println("  Biomes (" + s.getBiomes().size() + "):");
            for (String biome : s.getBiomes()) {
                pw.println("    - " + biome);
            }
            if (!s.getBiomeModLinked().isEmpty()) {
                pw.println("  Biomes (mod-linked) (" + s.getBiomeModLinked().size() + "):");
                for (String mod : s.getBiomeModLinked()) {
                    pw.println("    - " + mod);
                }
            }
        }

        printList(pw, "Entities (attacklock)", ent.getAttacklock());
        printSpawnlockEntries(pw, ent.getSpawnlock());
        printList(pw, "Entities (mod-linked)", ent.getModLinked());
        printOtherCategories(pw, s);

        if (s.hasDependencies()) {
            pw.println("  Dependencies (" + s.getDependencies().size() + " group(s)):");
            int gIdx = 0;
            for (DependencyGroup group : s.getDependencies()) {
                gIdx++;
                pw.println("    [Group " + gIdx + " | logic=" + group.getLogic() + "]");
                for (String stage : group.getStages())
                    pw.println("      stage: " + stage);
                for (IndividualStageDep dep : group.getIndividualStages())
                    pw.println("      individual_stage: " + dep.getStageId() + " (mode=" + dep.getMode() + ")");
                for (DependencyItem item : group.getItems())
                    pw.println("      item: " + item.getId() + " x" + item.getCount());
                for (DependencyItem tag : group.getItemTags())
                    pw.println("      item_tag: " + tag.getId() + " x" + tag.getCount());
                if (group.getXpLevel() != null)
                    pw.println("      xp_level: " + group.getXpLevel().getLevel() + (group.getXpLevel().isConsume() ? " (consume)" : ""));
                for (EntityKillDep kill : group.getEntityKills())
                    pw.println("      entity_kill: " + kill.getEntityId() + " x" + kill.getCount());
                for (StatDep stat : group.getStats())
                    pw.println("      stat: " + stat.getStatId() + " >= " + stat.getMinValue());
                for (ScoreboardDep sb : group.getScoreboard()) {
                    String holder = sb.isPlayerSelf() ? "<player>" : sb.getScoreHolder();
                    pw.println("      scoreboard: " + sb.getObjective() + " " + sb.getOp() + " "
                            + sb.getValue() + " (holder=" + holder + ")");
                }
                for (String adv : group.getAdvancements())
                    pw.println("      advancement: " + adv);
            }
        }

        AutoTrigger auto = s.getAutoTrigger();
        if (auto != null && !auto.isEmpty()) {
            String rawCombine = auto.getRawMode();
            String combine = auto.resolvedMode().serialize();
            if (rawCombine != null && !rawCombine.equalsIgnoreCase(combine)) {
                pw.println("  Auto-trigger (" + auto.getTriggers().size() + ", mode=" + combine
                        + " | raw=\"" + rawCombine + "\" → unknown, defaulted):");
            } else {
                pw.println("  Auto-trigger (" + auto.getTriggers().size() + ", mode=" + combine + "):");
            }
            for (TriggerCondition tc : auto.getTriggers()) {
                pw.println("    - " + tc.type() + ": " + tc);
            }
        }

        pw.println();
    }

    private static void printList(PrintWriter pw, String label, List<String> list) {
        if (list == null || list.isEmpty()) return;
        pw.println("  " + label + " (" + list.size() + "):");
        for (String entry : list) {
            pw.println("    - " + entry);
        }
    }

    private static void printItemEntries(PrintWriter pw, String label, List<ItemEntry> items) {
        if (items == null || items.isEmpty()) return;
        pw.println("  " + label + " (" + items.size() + "):");
        for (ItemEntry entry : items) {
            StringBuilder sb = new StringBuilder("    - ").append(entry.getId());
            if (entry.hasNbt()) sb.append(" [nbt]");
            if (entry.hasLockActions()) sb.append(" [lock: ").append(String.join(", ", entry.getLockActions())).append("]");
            pw.println(sb.toString());
        }
    }

    private static void printSpawnlockEntries(PrintWriter pw, List<net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        pw.println("  Entities (spawnlock) (" + entries.size() + "):");
        for (net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry entry : entries) {
            StringBuilder sb = new StringBuilder("    - ").append(entry.getId());
            if (entry.hasLockSources()) sb.append(" [sources: ").append(String.join(", ", entry.getLockSources())).append("]");
            pw.println(sb.toString());
        }
    }

    private static void printNamedLockEntries(PrintWriter pw, String label, List<NamedLockEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        pw.println("  " + label + " (" + entries.size() + "):");
        for (NamedLockEntry entry : entries) {
            StringBuilder sb = new StringBuilder("    - ").append(entry.getId());
            if (entry.hasLockActions()) sb.append(" [lock: ").append(String.join(", ", entry.getLockActions())).append("]");
            pw.println(sb.toString());
        }
    }

    private static String getNeoForgeVersion() {
        try {
            return ModList.get().getModContainerById("neoforge")
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ==================== Runtime logging ====================

    private static final ConcurrentLinkedQueue<String> RUNTIME_BUFFER = new ConcurrentLinkedQueue<>();
    private static final Map<String, Long> THROTTLE_MAP = new HashMap<>();
    private static final long THROTTLE_MS = 5000;
    private static volatile String runtimeFileName = null;
    private static volatile boolean headerWritten = false;

    public static void runtime(String category, String message) {
        if (!isRuntimeEnabled()) return;
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        RUNTIME_BUFFER.add("[" + timestamp + "] [" + category + "] " + message);
    }

    public static void runtime(String category, String playerName, String message) {
        if (!isRuntimeEnabled()) return;
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        RUNTIME_BUFFER.add("[" + timestamp + "] [" + category + "] <" + playerName + "> " + message);
    }

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

    public static void initRuntimeSession() {
        runtimeFileName = "runtime-" + LocalDateTime.now().format(FILE_FORMAT) + ".log";
        headerWritten = false;

        File logsDir = logsPath().toFile();
        if (logsDir.exists()) {
            cleanupOldLogs(logsDir, "runtime-", MAX_RUNTIME_FILES);
        }
    }

    public static void flushRuntimeBuffer() {
        if (RUNTIME_BUFFER.isEmpty()) return;
        if (runtimeFileName == null) initRuntimeSession();

        try {
            File logsDir = logsPath().toFile();
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

    public static void cleanupThrottleMap() {
        long now = System.currentTimeMillis();
        synchronized (THROTTLE_MAP) {
            THROTTLE_MAP.entrySet().removeIf(e -> (now - e.getValue()) > THROTTLE_MS * 2);
        }
    }

    private static boolean isRuntimeEnabled() {
        try {
            return Config.GAMEPLAY.enableRuntimeLogging.get();
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
