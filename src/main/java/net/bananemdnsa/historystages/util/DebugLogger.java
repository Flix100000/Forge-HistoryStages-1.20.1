package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.HistoryStagesFabric;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugLogger {
    private static final Map<String, Long> THROTTLE = new ConcurrentHashMap<>();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DebugLogger() {
    }

    public static void ensureLogDirectory() {
        try {
            Files.createDirectories(logDir());
        } catch (IOException exception) {
            HistoryStagesFabric.LOGGER.warn("Failed to create History Stages log directory.", exception);
        }
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
        appendRuntimeLine(category, actor + ": " + message);
    }

    public static void runtimeThrottled(String category, String key, String message) {
        long now = System.currentTimeMillis();
        Long last = THROTTLE.put(key, now);
        if (last == null || now - last > 30_000L) {
            HistoryStagesFabric.LOGGER.info("[{}] {}", category, message);
            appendRuntimeLine(category, message);
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

    public static void writeDiagnosticReport(Map<String, StageEntry> globalStages,
                                             Map<String, StageEntry> individualStages,
                                             List<StageManager.LoadingMessage> messages) {
        ensureLogDirectory();
        if (messages.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Path report = logDir().resolve("debug-" + FILE_TIME.format(now) + ".log");
        try {
            Files.writeString(report, buildDiagnosticReport(globalStages, individualStages, messages, now), StandardCharsets.UTF_8);
            HistoryStagesFabric.LOGGER.info("Wrote History Stages diagnostic report to {}.", report);
        } catch (IOException exception) {
            HistoryStagesFabric.LOGGER.warn("Failed to write History Stages diagnostic report.", exception);
        }
    }

    private static String buildDiagnosticReport(Map<String, StageEntry> globalStages,
                                                Map<String, StageEntry> individualStages,
                                                List<StageManager.LoadingMessage> messages,
                                                LocalDateTime now) {
        StringBuilder report = new StringBuilder();
        long errors = messages.stream().filter(message -> message.level() == StageManager.MessageLevel.ERROR).count();
        long warnings = messages.stream().filter(message -> message.level() == StageManager.MessageLevel.WARN).count();
        long info = messages.stream().filter(message -> message.level() == StageManager.MessageLevel.INFO).count();

        appendDivider(report);
        report.append("  History Stages - Diagnostic Report\n");
        report.append("  Generated: ").append(DISPLAY_TIME.format(now)).append('\n');
        report.append("  Mod Version: ").append(modVersion()).append('\n');
        report.append("  Minecraft: ").append(SharedConstants.getCurrentVersion().getName()).append('\n');
        report.append("  Fabric Loader: ").append(fabricLoaderVersion()).append('\n');
        appendDivider(report);
        report.append('\n');
        report.append("  Global stages loaded:     ").append(globalStages.size()).append('\n');
        report.append("  Individual stages loaded: ").append(individualStages.size()).append('\n');
        report.append("  Total issues:             ").append(messages.size())
                .append("  (Errors: ").append(errors)
                .append("  |  Warnings: ").append(warnings)
                .append("  |  Info: ").append(info).append(")\n\n");

        appendTotals(report, "Total entries across global stages:", globalStages);
        appendTotals(report, "Total entries across individual stages:", individualStages);

        appendDivider(report);
        report.append("  ISSUES\n");
        appendDivider(report);
        report.append('\n');
        for (StageManager.LoadingMessage message : messages) {
            report.append("  [").append(message.level()).append("]  ").append(message.message()).append('\n');
        }
        report.append('\n');

        appendStageSection(report, "LOADED GLOBAL STAGES", globalStages);
        appendStageSection(report, "LOADED INDIVIDUAL STAGES", individualStages);

        appendDivider(report);
        report.append("  This file was auto-generated by History Stages.\n");
        report.append("  It is created when issues are found during stage loading.\n");
        report.append("  Share this file when reporting bugs.\n\n");
        report.append("  Global stages:     config/historystages/global/\n");
        report.append("  Individual stages: config/historystages/individual/\n");
        report.append("  Log files:         config/historystages/logs/\n");
        report.append("  Disable chat debug messages: showDebugErrors=false\n");
        report.append("  in historystages-common.toml or config/historystages/config.json\n");
        appendDivider(report);
        return report.toString();
    }

    private static void appendTotals(StringBuilder report, String title, Map<String, StageEntry> stages) {
        Counts counts = count(stages);
        report.append("  ").append(title).append('\n');
        report.append("    Items: ").append(counts.items)
                .append("  |  Tags: ").append(counts.tags)
                .append("  |  Mods: ").append(counts.mods)
                .append("  |  Mod Exceptions: ").append(counts.modExceptions).append('\n');
        report.append("    Recipes: ").append(counts.recipes)
                .append("  |  Dimensions: ").append(counts.dimensions)
                .append("  |  Structures: ").append(counts.structures).append('\n');
        report.append("    Entities (attacklock): ").append(counts.attackLock)
                .append("  |  Entities (spawnlock): ").append(counts.spawnLock)
                .append("  |  Dependencies: ").append(counts.dependencies).append("\n\n");
    }

    private static Counts count(Map<String, StageEntry> stages) {
        Counts counts = new Counts();
        for (StageEntry stage : stages.values()) {
            counts.items += stage.getItemEntries().size();
            counts.tags += stage.getTagEntries().size();
            counts.mods += stage.getModEntries().size();
            counts.modExceptions += stage.getModExceptionEntries().size();
            counts.recipes += stage.getRecipes().size();
            counts.dimensions += stage.getDimensions().size();
            counts.structures += stage.getStructures().size();
            counts.attackLock += stage.getEntities().getAttacklock().size();
            counts.spawnLock += stage.getEntities().getSpawnlock().size();
            counts.dependencies += stage.getDependencies().stream().filter(group -> !group.isEmpty()).count();
        }
        return counts;
    }

    private static void appendStageSection(StringBuilder report, String title, Map<String, StageEntry> stages) {
        appendDivider(report);
        report.append("  ").append(title).append(" (").append(stages.size()).append(")\n");
        appendDivider(report);
        for (Map.Entry<String, StageEntry> entry : stages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            StageEntry stage = entry.getValue();
            report.append('\n');
            report.append("  ").append(entry.getKey()).append(" (").append(stage.getDisplayName()).append(")\n");
            appendList(report, "Items", stage.getAllItemIds());
            appendList(report, "Tags", stage.getTags());
            appendList(report, "Mods", stage.getMods());
            appendList(report, "Mod Exceptions", stage.getAllModExceptionIds());
            appendList(report, "Recipes", stage.getRecipes());
            appendList(report, "Dimensions", stage.getDimensions());
            appendList(report, "Structures", stage.getStructures());
            appendList(report, "Entities - Attacklock", stage.getEntities().getAttacklock());
            appendList(report, "Entities - Spawnlock", stage.getEntities().getSpawnlock());
            appendDependencies(report, stage.getDependencies());
        }
        report.append('\n');
    }

    private static void appendList(StringBuilder report, String title, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        report.append("  ").append(title).append(" (").append(values.size()).append("):\n");
        for (String value : values) {
            report.append("    - ").append(value).append('\n');
        }
    }

    private static void appendDependencies(StringBuilder report, List<DependencyGroup> groups) {
        List<DependencyGroup> active = groups.stream().filter(group -> !group.isEmpty()).toList();
        if (active.isEmpty()) {
            return;
        }
        report.append("  Dependencies (").append(active.size()).append(" group(s)):\n");
        for (int i = 0; i < active.size(); i++) {
            DependencyGroup group = active.get(i);
            report.append("    Group ").append(i + 1).append(" [").append(group.getLogic()).append("]\n");
            appendList(report, "    Stages", group.getStages());
            appendList(report, "    Advancements", group.getAdvancements());
            if (group.getXpLevel() != null) {
                report.append("    XP Level: ").append(group.getXpLevel().getLevel()).append('\n');
            }
            if (!group.getItems().isEmpty()) {
                report.append("    Items: ").append(group.getItems().size()).append('\n');
            }
            if (!group.getIndividualStages().isEmpty()) {
                report.append("    Individual Stages: ").append(group.getIndividualStages().size()).append('\n');
            }
            if (!group.getEntityKills().isEmpty()) {
                report.append("    Entity Kills: ").append(group.getEntityKills().size()).append('\n');
            }
            if (!group.getStats().isEmpty()) {
                report.append("    Stats: ").append(group.getStats().size()).append('\n');
            }
        }
    }

    private static void appendDivider(StringBuilder report) {
        report.append("============================================================\n");
    }

    private static void appendRuntimeLine(String category, String message) {
        if (!Config.COMMON.enableRuntimeLogging) {
            return;
        }
        ensureLogDirectory();
        String line = "[" + DISPLAY_TIME.format(LocalDateTime.now()) + "] [" + category + "] " + message + System.lineSeparator();
        try {
            Files.writeString(logDir().resolve("runtime.log"), line, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            HistoryStagesFabric.LOGGER.warn("Failed to append History Stages runtime log.", exception);
        }
    }

    private static Path logDir() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(HistoryStages.MOD_ID)
                .resolve("logs");
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(HistoryStages.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String fabricLoaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static final class Counts {
        private int items;
        private int tags;
        private int mods;
        private int modExceptions;
        private int recipes;
        private int dimensions;
        private int structures;
        private int attackLock;
        private int spawnLock;
        private long dependencies;
    }
}
