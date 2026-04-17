package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.platform.win32.WinDef;
import net.astr0.historystages.api.IStageManager;
import net.astr0.historystages.api.events.StageEvent;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import net.bananemdnsa.historystages.util.DebugLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class StageManager implements IStageManager {
    private static final Map<String, StageEntry> STAGES = new HashMap<>();
    private static final Map<String, StageEntry> INDIVIDUAL_STAGES = new HashMap<>();
    private static final List<LoadingMessage> LOADING_MESSAGES = new ArrayList<>();
    private static final Gson GSON = new Gson();

    private static final String GLOBAL_CONFIG_PATH = "global";
    private static final String INDIVIDUAL_CONFIG_PATH = "individual";

    public enum MessageLevel { ERROR, WARN, INFO }
    public record LoadingMessage(MessageLevel level, String message) {}

    private static void addMessage(MessageLevel level, String message) {
        LOADING_MESSAGES.add(new LoadingMessage(level, message));
    }

    private static final Set<String> KNOWN_KEYS = Set.of(
            "display_name", "research_time", "items", "tags", "mods",
            "mod_exceptions", "recipes", "dimensions", "entities", "dependencies"
    );
    private static final Set<String> KNOWN_ENTITY_KEYS = Set.of(
            "spawnlock", "attacklock", "modLinked"
    );

    /*
     * In my opinion(Astr0), moving towards singleton approaches will be cleaner overall.
     * Whilst they are often labelled as being "bad code", there are situations where they are highly appropriate
     * We need a StageManager that gives access to the same data globally, but all the data may not necessarily exist
     * at run-time (for example first load). Additionally, we need an instanced object for use with our external API.
     * I suggest we slowly refactor the code to this pattern. That way in our mod entry point we can create an instance,
     * initialize it from disk, and then pass it to the API. It will also let us create an event listener for world load,
     * and unload which can be used for saving the data cleanly.
     *
     * For now, this can exist here without breaking any of your static based functions.
     */
    private static StageManager INSTANCE;
    private StageManager() {}
    public static StageManager getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new StageManager();
        }

        return INSTANCE;
    }

    // =============================================
    // LOADING
    // =============================================

    public static void load() {
        STAGES.clear();
        INDIVIDUAL_STAGES.clear();
        LOADING_MESSAGES.clear();
        DebugLogger.clear();

        loadGlobalStages();

        DebugLogger.setStagesLoaded(STAGES.size());
        loadIndividual();
        checkCircularDependencies();
    }

    private static void loadGlobalStages() {
        File configDir = resolveConfigDir(GLOBAL_CONFIG_PATH);
        if (!configDir.exists()) configDir.mkdirs();

        File[] files = listJsonFiles(configDir);
        if (files == null) return;

        for (File file : files) {
            String id = stripJsonExtension(file.getName());
            validateFileName(id, file.getName());
            loadStageFile(file, id, false);
        }
    }

    private static void loadIndividual() {
        File configDir = resolveConfigDir(INDIVIDUAL_CONFIG_PATH);
        if (!configDir.exists()) {
            configDir.mkdirs();
            return;
        }

        File[] files = listJsonFiles(configDir);
        if (files == null) return;

        for (File file : files) {
            String id = stripJsonExtension(file.getName());
            validateFileName(id, file.getName());
            loadStageFile(file, id, true);
        }

        detectOverlaps();
        System.out.println("[HistoryStages] Individual stages loaded: " + INDIVIDUAL_STAGES.size());
    }

    private static void loadStageFile(File file, String id, boolean isIndividual) {
        String logCategory = isIndividual ? "Individual Stage Loading" : "Stage Loading";
        String fileLabel   = isIndividual ? "Individual file" : "File";

        try {
            String content = Files.readString(file.toPath());
            detectUnknownKeys(id, content);

            StageEntry entry = GSON.fromJson(content, StageEntry.class);
            if (entry != null) {
                if (isIndividual) {
                    stripUnsupportedIndividualCategories(id, entry);
                    validateAndAddIndividual(id, entry);
                } else {
                    validateAndAdd(id, entry);
                }
            } else {
                String msg = fileLabel + " '" + file.getName() + "' parsed as null (empty or invalid JSON)";
                addMessage(MessageLevel.ERROR, msg);
                DebugLogger.error(logCategory, msg);
            }
        } catch (Exception e) {
            String msg = "Error in " + (isIndividual ? "individual " : "") + "file: " + file.getName()
                    + " (Invalid JSON syntax, stage skipped)";
            addMessage(MessageLevel.ERROR, msg);
            DebugLogger.error(logCategory, msg + " — " + e.getMessage());
        }
    }

    // =============================================
    // FILE UTILITIES
    // =============================================

    private static File resolveConfigDir(String subdirectory) {
        return FMLPaths.CONFIGDIR.get().resolve("historystages").resolve(subdirectory).toFile();
    }

    private static File[] listJsonFiles(File dir) {
        return dir.listFiles((d, name) -> name.endsWith(".json") && !name.startsWith("_"));
    }

    private static String stripJsonExtension(String fileName) {
        return fileName.replace(".json", "");
    }

    // =============================================
    // CIRCULAR DEPENDENCY CHECK
    // =============================================

    /**
     * Detects circular dependencies between stages.
     * A cycle like A -> B -> A will produce an error message.
     */
    private static void checkCircularDependencies() {
        Map<String, Set<String>> graph = buildDependencyGraph(STAGES);
        graph.putAll(buildDependencyGraph(INDIVIDUAL_STAGES));

        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                List<String> path = new ArrayList<>();
                if (hasCycleDFS(node, graph, visited, inStack, path)) {
                    String cycle = String.join(" -> ", path);
                    String msg = "Circular dependency detected: " + cycle;
                    addMessage(MessageLevel.ERROR, msg);
                    DebugLogger.error("Circular Dependencies", msg);
                }
            }
        }
    }

    private static Map<String, Set<String>> buildDependencyGraph(Map<String, StageEntry> stages) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (Map.Entry<String, StageEntry> e : stages.entrySet()) {
            Set<String> refs = new HashSet<>();
            for (DependencyGroup group : e.getValue().getDependencies()) {
                refs.addAll(group.getReferencedStageIds());
            }
            if (!refs.isEmpty()) graph.put(e.getKey(), refs);
        }
        return graph;
    }

    private static boolean hasCycleDFS(String node, Map<String, Set<String>> graph,
                                       Set<String> visited, Set<String> inStack, List<String> path) {
        visited.add(node);
        inStack.add(node);
        path.add(node);

        for (String neighbor : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(neighbor)) {
                if (hasCycleDFS(neighbor, graph, visited, inStack, path)) return true;
            } else if (inStack.contains(neighbor)) {
                path.add(neighbor);
                return true;
            }
        }

        inStack.remove(node);
        path.remove(path.size() - 1);
        return false;
    }

    // =============================================
    // FILE NAME / KEY VALIDATION
    // =============================================

    private static void validateFileName(String id, String fileName) {
        if (!id.equals(id.toLowerCase())) {
            addMessage(MessageLevel.INFO, "File '" + fileName + "' contains uppercase letters. Lowercase recommended.");
            DebugLogger.info("File Names", "'" + fileName + "' contains uppercase letters. Use lowercase for consistency (e.g. '" + id.toLowerCase() + ".json').");
        }
        if (id.contains(" ")) {
            addMessage(MessageLevel.INFO, "File '" + fileName + "' contains spaces. Use underscores instead.");
            DebugLogger.info("File Names", "'" + fileName + "' contains spaces. Use underscores instead (e.g. '" + id.replace(" ", "_") + ".json').");
        }
        if (!id.matches("[a-zA-Z0-9_\\-]+")) {
            addMessage(MessageLevel.INFO, "File '" + fileName + "' contains special characters.");
            DebugLogger.info("File Names", "'" + fileName + "' contains special characters. Only use a-z, 0-9, _ and -.");
        }
    }

    private static void detectUnknownKeys(String stageId, String content) {
        try {
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            for (String key : json.keySet()) {
                if (!KNOWN_KEYS.contains(key)) {
                    addMessage(MessageLevel.WARN, "Unknown key '" + key + "' in stage '" + stageId + "'. Typo?");
                    DebugLogger.warn("Unknown Keys", "Unknown key '" + key + "' in stage '" + stageId
                            + "'. Known keys: " + KNOWN_KEYS + ". This key will be ignored.");
                }
            }
            if (json.has("entities") && json.get("entities").isJsonObject()) {
                JsonObject entities = json.getAsJsonObject("entities");
                for (String key : entities.keySet()) {
                    if (!KNOWN_ENTITY_KEYS.contains(key)) {
                        addMessage(MessageLevel.WARN, "Unknown entity key '" + key + "' in stage '" + stageId + "'. Typo?");
                        DebugLogger.warn("Unknown Keys", "Unknown key 'entities." + key + "' in stage '" + stageId
                                + "'. Known entity keys: " + KNOWN_ENTITY_KEYS + ".");
                    }
                }
            }
        } catch (Exception ignored) {
            // JSON parsing errors are handled in the main load loop
        }
    }

    // =============================================
    // ENTRY VALIDATION
    // =============================================

    private static void validateAndAdd(String stageId, StageEntry entry) {
        validateEntry(stageId, entry, "Stage", true);
        STAGES.put(stageId, entry);
        System.out.println("[HistoryStages] Stage loaded: " + stageId);
    }

    private static void validateAndAddIndividual(String stageId, StageEntry entry) {
        if (STAGES.containsKey(stageId)) {
            String msg = "Individual stage '" + stageId + "' has the same ID as a global stage. Individual stage skipped.";
            addMessage(MessageLevel.ERROR, msg);
            DebugLogger.error("Individual Stage Loading", msg);
            return;
        }
        validateEntry(stageId, entry, "Individual Stage", false);
        INDIVIDUAL_STAGES.put(stageId, entry);
        System.out.println("[HistoryStages] Individual Stage loaded: " + stageId);
    }

    /**
     * Shared validation logic for both global and individual stages.
     *
     * @param isGlobal if true, also validates: recipes, spawnlock, mod install check,
     *                 redundant entity detection, dependency references, and empty stage detection.
     */
    private static void validateEntry(String stageId, StageEntry entry, String context, boolean isGlobal) {
        if (entry.getDisplayName().equals("Unknown Stage")) {
            addMessage(MessageLevel.WARN, context + " '" + stageId + "' has no 'display_name'. Defaults to 'Unknown Stage'.");
            DebugLogger.warn("Missing Fields", context + " '" + stageId + "' has no 'display_name' set. It will show as 'Unknown Stage'.");
        }

        // Remove empty/blank entries
        removeEmptyItemEntries(entry.getItemEntries(), stageId);
        removeEmptyStrings(entry.getTags(), stageId, "tags");
        removeEmptyStrings(entry.getMods(), stageId, "mods");
        removeEmptyItemEntries(entry.getModExceptionEntries(), stageId);
        removeEmptyStrings(entry.getDimensions(), stageId, "dimensions");
        removeEmptyStrings(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");
        if (isGlobal) {
            removeEmptyStrings(entry.getRecipes(), stageId, "recipes");
            removeEmptyStrings(entry.getEntities().getSpawnlock(), stageId, "entities.spawnlock");
        }

        // Warn about duplicates
        checkDuplicateItems(entry.getItemEntries(), stageId);
        checkDuplicates(entry.getTags(), stageId, "tags");
        checkDuplicates(entry.getMods(), stageId, "mods");
        checkDuplicateItems(entry.getModExceptionEntries(), stageId);
        checkDuplicates(entry.getDimensions(), stageId, "dimensions");
        checkDuplicates(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");
        if (isGlobal) {
            checkDuplicates(entry.getRecipes(), stageId, "recipes");
            checkDuplicates(entry.getEntities().getSpawnlock(), stageId, "entities.spawnlock");
        }

        // Remove entries with invalid ResourceLocation format
        removeInvalidItemEntries(entry.getItemEntries(), "Item", stageId, context);
        removeInvalidResourceLocations(entry.getTags(), "Tag", stageId, context);
        removeInvalidResourceLocations(entry.getDimensions(), "Dimension", stageId, context);
        removeInvalidResourceLocations(entry.getEntities().getAttacklock(), "Entity attacklock", stageId, context);
        if (isGlobal) {
            removeInvalidResourceLocations(entry.getRecipes(), "Recipe", stageId, context);
            removeInvalidResourceLocations(entry.getEntities().getSpawnlock(), "Entity spawnlock", stageId, context);
        }

        // Mod format validation (individual stages skip the install-presence check)
        entry.getMods().removeIf(modId -> {
            if (modId == null || modId.isEmpty() || modId.contains(" ")) {
                addMessage(MessageLevel.WARN, "Mod ID '" + modId + "' invalid format (" + context + ": " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Mods", "Mod ID '" + modId + "' has invalid format (" + context + ": " + stageId + "). Removed.");
                return true;
            }
            if (isGlobal && !ModList.get().isLoaded(modId)) {
                addMessage(MessageLevel.INFO, "Mod '" + modId + "' not installed (" + context + ": " + stageId + "). Entry kept.");
                DebugLogger.info("Missing Mods", "Mod '" + modId + "' is not installed (" + context + ": " + stageId
                        + "). Entry kept — will apply if mod is added later.");
            }
            return false;
        });

        // Mod exceptions: must be valid ResourceLocations and belong to a locked mod
        Set<String> lockedMods = new HashSet<>(entry.getMods());
        entry.getModExceptionEntries().removeIf(exceptionEntry -> {
            String exItemId = exceptionEntry.getId();
            if (!ResourceLocation.isValidResourceLocation(exItemId)) {
                addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' invalid format (" + context + ": " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Mod Exceptions", "Mod exception '" + exItemId
                        + "' is not a valid ResourceLocation (" + context + ": " + stageId + "). Removed.");
                return true;
            }
            ResourceLocation rl = ResourceLocation.parse(exItemId);
            if (!lockedMods.contains(rl.getNamespace())) {
                addMessage(MessageLevel.ERROR, "Mod exception '" + exItemId + "' does not belong to a locked mod (" + context + ": " + stageId + "). Removed.");
                DebugLogger.error("Invalid Mod Exceptions", "Mod exception '" + exItemId + "' belongs to mod '"
                        + rl.getNamespace() + "' which is not in the 'mods' list (" + context + ": " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        if (isGlobal) {
            // Warn about entities redundantly in both lists (spawnlock implies attacklock)
            for (String entityId : entry.getEntities().getSpawnlock()) {
                if (entry.getEntities().getAttacklock().contains(entityId)) {
                    addMessage(MessageLevel.INFO, "Entity '" + entityId + "' in both attacklock and spawnlock (Stage: " + stageId + "). Redundant.");
                    DebugLogger.info("Redundant Entities", "Entity '" + entityId + "' is in both attacklock and spawnlock (Stage: "
                            + stageId + "). Spawnlock already implies attacklock — the attacklock entry is redundant.");
                }
            }

            // Dependencies validation
            if (entry.hasDependencies()) {
                for (DependencyGroup group : entry.getDependencies()) {
                    group.getItems().removeIf(depItem -> {
                        if (depItem.getId() == null || !ResourceLocation.isValidResourceLocation(depItem.getId())) {
                            addMessage(MessageLevel.WARN, "Dependency item '" + depItem.getId() + "' invalid format (Stage: " + stageId + "). Removed.");
                            return true;
                        }
                        return false;
                    });
                    group.getEntityKills().removeIf(kill -> {
                        if (kill.getEntityId() == null || !ResourceLocation.isValidResourceLocation(kill.getEntityId())) {
                            addMessage(MessageLevel.WARN, "Dependency entity kill '" + kill.getEntityId() + "' invalid format (Stage: " + stageId + "). Removed.");
                            return true;
                        }
                        return false;
                    });
                    group.getStats().removeIf(stat -> {
                        if (stat.getStatId() == null || !ResourceLocation.isValidResourceLocation(stat.getStatId())) {
                            addMessage(MessageLevel.WARN, "Dependency stat '" + stat.getStatId() + "' invalid format (Stage: " + stageId + "). Removed.");
                            return true;
                        }
                        return false;
                    });
                    group.getAdvancements().removeIf(adv -> {
                        if (adv == null || !ResourceLocation.isValidResourceLocation(adv)) {
                            addMessage(MessageLevel.WARN, "Dependency advancement '" + adv + "' invalid format (Stage: " + stageId + "). Removed.");
                            return true;
                        }
                        return false;
                    });
                    for (String depStageId : group.getStages()) {
                        if (!STAGES.containsKey(depStageId)) {
                            addMessage(MessageLevel.INFO, "Dependency stage '" + depStageId + "' not found (Stage: " + stageId + "). May load later.");
                        }
                    }
                }
            }

            // Research time
            if (entry.getResearchTime() < 0) {
                addMessage(MessageLevel.INFO, "Stage '" + stageId + "' has negative research_time (" + entry.getResearchTime() + "). Using global default.");
                DebugLogger.info("Configuration", "Stage '" + stageId + "' has negative research_time ("
                        + entry.getResearchTime() + "). Falling back to global default.");
            }

            // Empty stage check
            int totalEntries = entry.getItemEntries().size() + entry.getTags().size() + entry.getMods().size()
                    + entry.getModExceptionEntries().size() + entry.getRecipes().size() + entry.getDimensions().size()
                    + entry.getEntities().getAttacklock().size() + entry.getEntities().getSpawnlock().size();
            if (totalEntries == 0) {
                addMessage(MessageLevel.INFO, "Stage '" + stageId + "' has no content. It won't lock anything.");
                DebugLogger.info("Empty Stages", "Stage '" + stageId + "' has no content at all. It will be loaded but won't lock anything.");
            }
        } else {
            // Individual-only checks
            if (entry.getResearchTime() < 0) {
                addMessage(MessageLevel.INFO, "Individual stage '" + stageId + "' has negative research_time. Using global default.");
            }
        }
    }

    // =============================================
    // VALIDATION HELPERS
    // =============================================

    private static void removeInvalidResourceLocations(List<String> list, String fieldLabel, String stageId, String context) {
        list.removeIf(id -> {
            if (!ResourceLocation.isValidResourceLocation(id)) {
                addMessage(MessageLevel.WARN, fieldLabel + " '" + id + "' invalid format (" + context + ": " + stageId + "). Removed.");
                DebugLogger.warn("Invalid " + fieldLabel + "s", fieldLabel + " '" + id
                        + "' is not a valid ResourceLocation (" + context + ": " + stageId + "). Removed.");
                return true;
            }
            return false;
        });
    }

    private static void removeInvalidItemEntries(List<ItemEntry> list, String fieldLabel, String stageId, String context) {
        list.removeIf(item -> {
            String itemId = item.getId();
            if (!ResourceLocation.isValidResourceLocation(itemId)) {
                addMessage(MessageLevel.WARN, fieldLabel + " '" + itemId + "' invalid format (" + context + ": " + stageId + "). Removed.");
                DebugLogger.warn("Invalid " + fieldLabel + "s", fieldLabel + " '" + itemId
                        + "' is not a valid ResourceLocation (" + context + ": " + stageId + "). Removed.");
                return true;
            }
            return false;
        });
    }

    private static void removeEmptyItemEntries(List<ItemEntry> list, String stageId) {
        int before = list.size();
        list.removeIf(e -> e.getId() == null || e.getId().isBlank());
        int removed = before - list.size();
        if (removed > 0) {
            addMessage(MessageLevel.WARN, "Removed " + removed + " empty item(s) from 'items' (Stage: " + stageId + ").");
            DebugLogger.warn("Empty Entries", "Removed " + removed + " empty item(s) from 'items' (Stage: " + stageId + ").");
        }
    }

    private static void removeEmptyStrings(List<String> list, String stageId, String field) {
        int before = list.size();
        list.removeIf(val -> val == null || val.isBlank());
        int removed = before - list.size();
        if (removed > 0) {
            addMessage(MessageLevel.WARN, "Removed " + removed + " empty string(s) from '" + field + "' (Stage: " + stageId + ").");
            DebugLogger.warn("Empty Entries", "Removed " + removed + " empty/blank string(s) from '" + field + "' (Stage: " + stageId + ").");
        }
    }

    private static void checkDuplicateItems(List<ItemEntry> list, String stageId) {
        Set<String> seen = new HashSet<>();
        for (ItemEntry entry : list) {
            if (!seen.add(entry.getId())) {
                addMessage(MessageLevel.INFO, "Duplicate '" + entry.getId() + "' in 'items' (Stage: " + stageId + ").");
                DebugLogger.info("Duplicates", "Duplicate entry '" + entry.getId() + "' in 'items' (Stage: " + stageId + ").");
            }
        }
    }

    private static void checkDuplicates(List<String> list, String stageId, String field) {
        Set<String> seen = new HashSet<>();
        for (String val : list) {
            if (!seen.add(val)) {
                addMessage(MessageLevel.INFO, "Duplicate '" + val + "' in '" + field + "' (Stage: " + stageId + ").");
                DebugLogger.info("Duplicates", "Duplicate entry '" + val + "' in '" + field + "' (Stage: " + stageId
                        + "). Only the first occurrence will be used.");
            }
        }
    }

    // =============================================
    // REGISTRY VALIDATION
    // =============================================

    /**
     * Validates stage entries against the actual registries.
     * Must be called AFTER registries are fully loaded (e.g. on world load),
     * NOT during mod construction when load() runs.
     */
    public static void validateAgainstRegistries() {
        for (Map.Entry<String, StageEntry> e : STAGES.entrySet()) {
            validateStageAgainstRegistries(e.getKey(), e.getValue(), "Stage");
        }
        for (Map.Entry<String, StageEntry> e : INDIVIDUAL_STAGES.entrySet()) {
            validateStageAgainstRegistries(e.getKey(), e.getValue(), "Individual Stage");
        }
    }

    private static void validateStageAgainstRegistries(String stageId, StageEntry entry, String context) {
        for (String itemId : entry.getAllItemIds()) {
            if (!ResourceLocation.isValidResourceLocation(itemId)) continue;
            if (!ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(itemId))) {
                addMessage(MessageLevel.WARN, "Item '" + itemId + "' does not exist in registry (" + context + ": " + stageId + ").");
                DebugLogger.warn("Unknown Items", "Item '" + itemId + "' does not exist in the item registry ("
                        + context + ": " + stageId + "). Typo or missing mod?");
            }
        }

        for (String exItemId : entry.getAllModExceptionIds()) {
            if (!ResourceLocation.isValidResourceLocation(exItemId)) continue;
            if (!ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(exItemId))) {
                addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' does not exist in registry (" + context + ": " + stageId + ").");
                DebugLogger.warn("Unknown Mod Exceptions", "Mod exception '" + exItemId + "' does not exist in the item registry ("
                        + context + ": " + stageId + "). Typo or missing mod?");
            }
        }

        validateEntityListAgainstRegistry(entry.getEntities().getAttacklock(), stageId, context, "attacklock");
        validateEntityListAgainstRegistry(entry.getEntities().getSpawnlock(), stageId, context, "spawnlock");
    }

    private static void validateEntityListAgainstRegistry(List<String> entityIds, String stageId, String context, String lockType) {
        for (String entityId : entityIds) {
            if (!ResourceLocation.isValidResourceLocation(entityId)) continue;
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.parse(entityId))) {
                addMessage(MessageLevel.WARN, "Entity '" + entityId + "' does not exist in registry (" + context + ": " + stageId + ", " + lockType + ").");
                DebugLogger.warn("Unknown Entities", "Entity '" + entityId + "' does not exist in the entity registry ("
                        + context + ": " + stageId + ", " + lockType + "). Typo or missing mod?");
            }
        }
    }

    // =============================================
    // STAGE QUERIES — GLOBAL
    // =============================================

    public static Map<String, StageEntry> getStages() {
        return STAGES;
    }

    /**
     * Replaces all stage definitions with the given map.
     * Used on the client side to sync stage definitions from the server in multiplayer.
     */
    public static void setStages(Map<String, StageEntry> stages) {
        STAGES.clear();
        if (stages != null) STAGES.putAll(stages);
    }

    public static String getStageForItemOrMod(String itemId, String modId) {
        for (var entry : STAGES.entrySet()) {
            StageEntry data = entry.getValue();

            if (data.getItems() != null && data.getItems().contains(itemId)) return entry.getKey();
            if (data.getMods() != null && data.getMods().contains(modId) && !data.isModExcepted(itemId, null)) return entry.getKey();

            if (data.getTags() != null) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
                if (item != null) {
                    for (String tagId : data.getTags()) {
                        var tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
                        if (item.builtInRegistryHolder().is(tagKey)) return entry.getKey();
                    }
                }
            }
        }
        return null;
    }

    public static List<String> getAllStagesForItemOrMod(String itemId, String modId) {
        return getAllStagesForItemOrMod(itemId, modId, null);
    }

    public static List<String> getAllStagesForItemOrMod(String itemId, String modId, ItemStack stack) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        return collectMatchingStages(STAGES, itemId, modId, stack, item);
    }

    public static List<String> getAllStagesForAttackLockedEntity(String entityId) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            EntityLocks locks = entry.getValue().getEntities();
            // Spawnlock entities are implicitly also attack-locked
            if (locks.getAttacklock().contains(entityId) || locks.getSpawnlock().contains(entityId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static List<String> getAllStagesForSpawnLockedEntity(String entityId) {
        return collectStagesWhere(STAGES, entry -> entry.getEntities().getSpawnlock().contains(entityId));
    }

    public static String getStageForDimension(String dimensionId) {
        for (var entry : STAGES.entrySet()) {
            if (entry.getValue().getDimensions() != null && entry.getValue().getDimensions().contains(dimensionId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static List<String> getAllStagesForDimension(String dimensionId) {
        return collectStagesWhere(STAGES, entry -> entry.getDimensions() != null && entry.getDimensions().contains(dimensionId));
    }

    /**
     * Returns the research time in ticks for a stage.
     * Uses the stage's own research_time if > 0, otherwise falls back to the global config.
     */
    public static int getResearchTimeInTicks(String stageId) {
        return resolveResearchTimeInTicks(STAGES.get(stageId));
    }

    public static boolean saveStage(String stageId, StageEntry entry) {
        return persistStage(stageId, entry, STAGES, GLOBAL_CONFIG_PATH, "Stage Save");
    }

    public static boolean deleteStage(String stageId) {
        return removeStage(stageId, STAGES, GLOBAL_CONFIG_PATH, "Stage Delete");
    }

    public static List<String> getStageOrder() {
        return resolveStageOrder(STAGES, GLOBAL_CONFIG_PATH);
    }

    public static boolean isRecipeIdLockedForServer(String recipeId) {
        return isRecipeIdLocked(recipeId, false);
    }

    /**
     * Checks if a recipe ID is locked — works on both client and server.
     * Uses ClientStageCache on the client, SERVER_CACHE on the server.
     */
    public static boolean isRecipeIdLocked(String recipeId, boolean isClientSide) {
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            if (entry.getValue().getRecipes().contains(recipeId) && !isStageUnlocked(entry.getKey(), isClientSide)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isItemLockedForServer(ItemStack stack) {
        return isItemLocked(stack, false);
    }

    /**
     * Checks if an item is locked — works on both client and server.
     * Uses ClientStageCache on the client, SERVER_CACHE on the server.
     */
    public static boolean isItemLocked(ItemStack stack, boolean isClientSide) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res == null) return false;

        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace(), stack);
        return requiredStages.stream().anyMatch(stage -> !isStageUnlocked(stage, isClientSide));
    }

    // =============================================
    // STAGE QUERIES — INDIVIDUAL
    // =============================================

    public static Map<String, StageEntry> getIndividualStages() {
        return INDIVIDUAL_STAGES;
    }

    public static void setIndividualStages(Map<String, StageEntry> stages) {
        INDIVIDUAL_STAGES.clear();
        if (stages != null) INDIVIDUAL_STAGES.putAll(stages);
    }

    public static List<String> getAllIndividualStagesForItemOrMod(String itemId, String modId) {
        return getAllIndividualStagesForItemOrMod(itemId, modId, null);
    }

    public static List<String> getAllIndividualStagesForItemOrMod(String itemId, String modId, ItemStack stack) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        return collectMatchingStages(INDIVIDUAL_STAGES, itemId, modId, stack, item);
    }

    public static List<String> getAllIndividualStagesForAttackLockedEntity(String entityId) {
        return collectStagesWhere(INDIVIDUAL_STAGES, entry -> entry.getEntities().getAttacklock().contains(entityId));
    }

    public static List<String> getAllIndividualStagesForDimension(String dimensionId) {
        return collectStagesWhere(INDIVIDUAL_STAGES, entry -> entry.getDimensions() != null && entry.getDimensions().contains(dimensionId));
    }

    /**
     * Returns the research time in ticks for an individual stage.
     * Falls back to global config if stage has no custom time.
     */
    public static int getIndividualResearchTimeInTicks(String stageId) {
        return resolveResearchTimeInTicks(INDIVIDUAL_STAGES.get(stageId));
    }

    public static boolean saveIndividualStage(String stageId, StageEntry entry) {
        return persistStage(stageId, entry, INDIVIDUAL_STAGES, INDIVIDUAL_CONFIG_PATH, "Individual Stage Save");
    }

    public static boolean deleteIndividualStage(String stageId) {
        return removeStage(stageId, INDIVIDUAL_STAGES, INDIVIDUAL_CONFIG_PATH, "Individual Stage Delete");
    }

    public static List<String> getIndividualStageOrder() {
        return resolveStageOrder(INDIVIDUAL_STAGES, INDIVIDUAL_CONFIG_PATH);
    }

    /**
     * Checks if a stage ID belongs to an individual stage.
     */
    public static boolean isIndividualStage(String stageId) {
        return INDIVIDUAL_STAGES.containsKey(stageId);
    }

    // =============================================
    // INDIVIDUAL STAGE — LOADING SPECIFICS
    // =============================================

    private static void stripUnsupportedIndividualCategories(String stageId, StageEntry entry) {
        if (entry.getRecipes() != null && !entry.getRecipes().isEmpty()) {
            String msg = "Individual stage '" + stageId + "' contains 'recipes' — not supported for individual stages. Entries removed.";
            addMessage(MessageLevel.ERROR, msg);
            DebugLogger.error("Individual Stage Loading", msg);
            entry.getRecipes().clear();
        }
        if (entry.getEntities().getSpawnlock() != null && !entry.getEntities().getSpawnlock().isEmpty()) {
            String msg = "Individual stage '" + stageId + "' contains 'entities.spawnlock' — not supported for individual stages. Entries removed.";
            addMessage(MessageLevel.ERROR, msg);
            DebugLogger.error("Individual Stage Loading", msg);
            entry.getEntities().getSpawnlock().clear();
        }
    }

    /**
     * Checks for overlap between individual and global stages.
     * Global stages have loading priority — conflicting individual entries are removed with an error.
     * Note: Dimensions and entities are allowed to overlap.
     */
    private static void detectOverlaps() {
        Map<String, String> globalItemMap = new HashMap<>();
        Map<String, String> globalTagMap  = new HashMap<>();
        Map<String, String> globalModMap  = new HashMap<>();

        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            String gStageId = entry.getKey();
            entry.getValue().getAllItemIds().forEach(item -> globalItemMap.put(item, gStageId));
            entry.getValue().getTags().forEach(tag -> globalTagMap.put(tag, gStageId));
            entry.getValue().getMods().forEach(mod -> globalModMap.put(mod, gStageId));
        }

        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            String iStageId = entry.getKey();
            StageEntry iEntry = entry.getValue();
            removeOverlappingEntries(iEntry.getItemEntries(), globalItemMap, iStageId, "item", ItemEntry::getId);
            removeOverlappingStringEntries(iEntry.getTags(), globalTagMap, iStageId, "tag");
            removeOverlappingStringEntries(iEntry.getMods(), globalModMap, iStageId, "mod");
        }
    }

    private static <T> void removeOverlappingEntries(List<T> list, Map<String, String> globalMap,
                                                     String stageId, String label, Function<T, String> keyOf) {
        list.removeIf(item -> {
            String conflict = globalMap.get(keyOf.apply(item));
            if (conflict != null) {
                String msg = "Individual stage '" + stageId + "' " + label + " '" + keyOf.apply(item)
                        + "' conflicts with global stage '" + conflict + "'. Individual entry skipped.";
                addMessage(MessageLevel.ERROR, msg);
                DebugLogger.error("Overlap Detection", msg);
                return true;
            }
            return false;
        });
    }

    private static void removeOverlappingStringEntries(List<String> list, Map<String, String> globalMap,
                                                       String stageId, String label) {
        removeOverlappingEntries(list, globalMap, stageId, label, s -> s);
    }

    // =============================================
    // SHARED PRIVATE UTILITIES
    // =============================================

    /**
     * Collects stage IDs from the given map where the stage matches the given item, mod, or tag.
     * Used by both global and individual getAllStagesForItemOrMod methods.
     */
    private static List<String> collectMatchingStages(
            Map<String, StageEntry> stages, String itemId, String modId, ItemStack stack, Item item) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            if (stageMatchesItemOrMod(entry.getValue(), itemId, modId, stack, item)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private static boolean stageMatchesItemOrMod(StageEntry data, String itemId, String modId,
                                                 ItemStack stack, Item item) {
        // Check item entries (with optional NBT matching)
        for (ItemEntry itemEntry : data.getItemEntries()) {
            if (itemEntry.getId().equals(itemId)) {
                if (!itemEntry.hasNbt() || (stack != null && NbtMatcher.matches(stack, itemEntry.getNbt()))) {
                    return true;
                }
            }
        }
        // Check mod ID with exception guard
        if (data.getMods().contains(modId) && !data.isModExcepted(itemId, stack)) return true;

        // Check tags
        if (item != null && data.getTags() != null) {
            for (String tagId : data.getTags()) {
                var tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
                if (item.builtInRegistryHolder().is(tagKey)) return true;
            }
        }
        return false;
    }

    /** Collects stage IDs from the given map where the predicate on StageEntry is satisfied. */
    private static List<String> collectStagesWhere(Map<String, StageEntry> stages, Predicate<StageEntry> predicate) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            if (predicate.test(entry.getValue())) result.add(entry.getKey());
        }
        return result;
    }

    /** Returns whether the given stage is currently unlocked, routing to client or server cache. */
    private static boolean isStageUnlocked(String stageId, boolean isClientSide) {
        return isClientSide
                ? ClientStageCache.isStageUnlocked(stageId)
                : StageData.SERVER_CACHE.contains(stageId);
    }

    /** Returns research time in ticks, falling back to the global config default. */
    private static int resolveResearchTimeInTicks(StageEntry entry) {
        if (entry != null && entry.getResearchTime() > 0) {
            return entry.getResearchTime() * 20;
        }
        return Config.COMMON.researchTimeInSeconds.get() * 20;
    }

    private static boolean persistStage(String stageId, StageEntry entry,
                                        Map<String, StageEntry> map, String configPath, String logCategory) {
        File configDir = resolveConfigDir(configPath);
        if (!configDir.exists()) configDir.mkdirs();

        File file = new File(configDir, stageId + ".json");
        try (Writer writer = new FileWriter(file)) {
            writer.write(entry.toJson());
            map.put(stageId, entry);
            DebugLogger.runtime(logCategory, "Saved stage '" + stageId + "' to " + file.getName());
            return true;
        } catch (Exception e) {
            System.err.println("[HistoryStages] Failed to save stage: " + stageId + " - " + e.getMessage());
            DebugLogger.error(logCategory, "Failed to save stage '" + stageId + "': " + e.getMessage());
            if (map == STAGES) DebugLogger.writeLogFile(STAGES, INDIVIDUAL_STAGES);
            return false;
        }
    }

    private static boolean removeStage(String stageId, Map<String, StageEntry> map,
                                       String configPath, String logCategory) {
        File file = new File(resolveConfigDir(configPath), stageId + ".json");
        if (file.exists() && file.delete()) {
            map.remove(stageId);
            DebugLogger.runtime(logCategory, "Deleted stage '" + stageId + "'");
            return true;
        }
        return false;
    }

    private static List<String> resolveStageOrder(Map<String, StageEntry> map, String configPath) {
        File configDir = resolveConfigDir(configPath);
        if (!configDir.exists()) return new ArrayList<>(map.keySet());

        File[] files = listJsonFiles(configDir);
        if (files == null) return new ArrayList<>(map.keySet());

        Arrays.sort(files);
        List<String> order = new ArrayList<>();
        for (File file : files) {
            String id = stripJsonExtension(file.getName());
            if (map.containsKey(id)) order.add(id);
        }
        // Safety: catch any in-memory stages with no corresponding file
        for (String id : map.keySet()) {
            if (!order.contains(id)) order.add(id);
        }
        return order;
    }

    // =============================================
    // MISC PUBLIC API
    // =============================================

    public static List<LoadingMessage> getLoadingMessages() {
        return LOADING_MESSAGES;
    }

    public static void reloadStages() {
        load();
    }

    public static StageEntry getStageEntryForLevel(ServerLevel level, String stage) {
        if (!getStages().containsKey(stage)) return null;

        return getStages().get(stage);
    }

    // =============================================
    // CLIENT MOD API IMPLEMENTATION
    // These functions implement the IStageManager interface.
    // They will allow client mods to interact with this one
    // We should also aim to use these as much as possible internally
    // for consistency
    // =============================================
    @Override
    public boolean isStageUnlockedForPlayer(ServerPlayer player, String stage) {
        return false;
    }

    @Override
    public boolean isStageUnlockedGlobally(String stage) {
        return false;
    }

    @Override
    public void unlockStageForPlayer(ServerPlayer player, String stage) {

        var entry = getStageEntryForLevel(player.serverLevel(), stage);
        if (entry == null) { return; }

        StageData data = StageData.get(player.serverLevel());
        String displayName = entry.getDisplayName();

        data.addStage(stage);
        MinecraftForge.EVENT_BUS.post(new StageEvent.Unlocked(stage, displayName));

        data.setDirty();
        StageData.refreshCache(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
    }

    @Override
    public void unlockStageGlobally(String stage) {

    }

    @Override
    public void lockStageForPlayer(ServerPlayer player, String stage) {
        if (!getStages().containsKey(stage)) return;

        StageData data = StageData.get(player.serverLevel());
        var entry = StageManager.getStages().get(stage);
        String displayName = entry.getDisplayName();

        data.removeStage(stage);
        MinecraftForge.EVENT_BUS.post(new StageEvent.Locked(stage, displayName));

        data.setDirty();
        StageData.refreshCache(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
    }

    @Override
    public void lockStageGlobally(String stage) {

    }
}