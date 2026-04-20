package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.data.dependency.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import net.bananemdnsa.historystages.util.DebugLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StageManager {
    private static final Map<String, StageEntry> STAGES = new HashMap<>();
    private static final Map<String, StageEntry> INDIVIDUAL_STAGES = new HashMap<>();
    private static final List<LoadingMessage> LOADING_MESSAGES = new ArrayList<>();
    private static final Gson GSON = new Gson();

    // Dual-phase: item/tag/mod ID → set of global stage IDs that share the entry with an individual stage
    private static final Map<String, Set<String>> DUAL_PHASE_ITEMS = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_TAGS = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_MODS = new HashMap<>();

    public enum MessageLevel { ERROR, WARN, INFO }
    public record LoadingMessage(MessageLevel level, String message) {}

    private static void addMessage(MessageLevel level, String message) {
        LOADING_MESSAGES.add(new LoadingMessage(level, message));
    }


    private static final Set<String> KNOWN_KEYS = Set.of(
            "display_name", "research_time", "items", "tags", "mods",
            "mod_exceptions", "recipes", "dimensions", "structures", "entities", "dependencies"
    );
    private static final Set<String> KNOWN_ENTITY_KEYS = Set.of(
            "spawnlock", "attacklock", "modLinked"
    );

    public static void load() {
        STAGES.clear();
        INDIVIDUAL_STAGES.clear();
        DUAL_PHASE_ITEMS.clear();
        DUAL_PHASE_TAGS.clear();
        DUAL_PHASE_MODS.clear();
        LOADING_MESSAGES.clear();
        DebugLogger.clear();

        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("global").toFile();
        if (!configDir.exists()) configDir.mkdirs();

        File[] files = configDir.listFiles((dir, name) ->
                name.endsWith(".json") && !name.startsWith("_")
        );

        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".json", "");

            // Validate file name
            validateFileName(id, file.getName());

            try (Reader reader = new FileReader(file)) {
                // Parse raw JSON first to detect unknown keys
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                detectUnknownKeys(id, content);

                // Now parse into StageEntry
                StageEntry entry = GSON.fromJson(content, StageEntry.class);

                if (entry != null) {
                    validateAndAdd(id, entry);
                } else {
                    String msg = "File '" + file.getName() + "' parsed as null (empty or invalid JSON)";
                    addMessage(MessageLevel.ERROR, msg);
                    DebugLogger.error("Stage Loading", msg);
                }
            } catch (Exception e) {
                String msg = "Error in file: " + file.getName() + " (Invalid JSON syntax, stage skipped)";
                addMessage(MessageLevel.ERROR, msg);
                DebugLogger.error("Stage Loading", msg + " — " + e.getMessage());
            }
        }

        DebugLogger.setStagesLoaded(STAGES.size());

        // Load individual stages after global stages
        loadIndividual();

        // Check for circular dependencies across all stages
        checkCircularDependencies();
    }

    /**
     * Detects circular dependencies between stages.
     * A cycle like A -> B -> A will produce an error message.
     */
    private static void checkCircularDependencies() {
        Map<String, Set<String>> graph = new HashMap<>();

        // Build adjacency list from all stages (global + individual)
        for (Map.Entry<String, StageEntry> e : STAGES.entrySet()) {
            Set<String> refs = new HashSet<>();
            for (DependencyGroup group : e.getValue().getDependencies()) {
                refs.addAll(group.getReferencedStageIds());
            }
            if (!refs.isEmpty()) graph.put(e.getKey(), refs);
        }
        for (Map.Entry<String, StageEntry> e : INDIVIDUAL_STAGES.entrySet()) {
            Set<String> refs = new HashSet<>();
            for (DependencyGroup group : e.getValue().getDependencies()) {
                refs.addAll(group.getReferencedStageIds());
            }
            if (!refs.isEmpty()) graph.put(e.getKey(), refs);
        }

        // DFS cycle detection
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

    private static boolean hasCycleDFS(String node, Map<String, Set<String>> graph,
                                       Set<String> visited, Set<String> inStack, List<String> path) {
        visited.add(node);
        inStack.add(node);
        path.add(node);

        Set<String> neighbors = graph.getOrDefault(node, Set.of());
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                if (hasCycleDFS(neighbor, graph, visited, inStack, path)) {
                    return true;
                }
            } else if (inStack.contains(neighbor)) {
                path.add(neighbor);
                return true;
            }
        }

        inStack.remove(node);
        path.remove(path.size() - 1);
        return false;
    }

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
                    DebugLogger.warn("Unknown Keys", "Unknown key '" + key + "' in stage '" + stageId + "'. Known keys: " + KNOWN_KEYS + ". This key will be ignored.");
                }
            }
            // Check entity sub-keys
            if (json.has("entities") && json.get("entities").isJsonObject()) {
                JsonObject entities = json.getAsJsonObject("entities");
                for (String key : entities.keySet()) {
                    if (!KNOWN_ENTITY_KEYS.contains(key)) {
                        addMessage(MessageLevel.WARN, "Unknown entity key '" + key + "' in stage '" + stageId + "'. Typo?");
                        DebugLogger.warn("Unknown Keys", "Unknown key 'entities." + key + "' in stage '" + stageId + "'. Known entity keys: " + KNOWN_ENTITY_KEYS + ".");
                    }
                }
            }
        } catch (Exception ignored) {
            // JSON parsing errors are handled in the main load loop
        }
    }

    private static void validateAndAdd(String stageId, StageEntry entry) {

        // --- Display Name ---
        if (entry.getDisplayName().equals("Unknown Stage")) {
            addMessage(MessageLevel.WARN, "Stage '" + stageId + "' has no 'display_name'. Defaults to 'Unknown Stage'.");
            DebugLogger.warn("Missing Fields", "Stage '" + stageId + "' has no 'display_name' set. It will show as 'Unknown Stage'.");
        }

        // --- Empty strings & duplicates helper ---
        removeEmptyItemEntries(entry.getItemEntries(), stageId);
        removeEmptyStrings(entry.getTags(), stageId, "tags");
        removeEmptyStrings(entry.getMods(), stageId, "mods");
        removeEmptyItemEntries(entry.getModExceptionEntries(), stageId);
        removeEmptyStrings(entry.getRecipes(), stageId, "recipes");
        removeEmptyStrings(entry.getDimensions(), stageId, "dimensions");
        removeEmptyStrings(entry.getStructures(), stageId, "structures");
        removeEmptyStrings(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");
        removeEmptyStrings(entry.getEntities().getSpawnlock(), stageId, "entities.spawnlock");

        checkDuplicateItems(entry.getItemEntries(), stageId);
        checkDuplicates(entry.getTags(), stageId, "tags");
        checkDuplicates(entry.getMods(), stageId, "mods");
        checkDuplicateItems(entry.getModExceptionEntries(), stageId);
        checkDuplicates(entry.getRecipes(), stageId, "recipes");
        checkDuplicates(entry.getDimensions(), stageId, "dimensions");
        checkDuplicates(entry.getStructures(), stageId, "structures");
        checkDuplicates(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");
        checkDuplicates(entry.getEntities().getSpawnlock(), stageId, "entities.spawnlock");

        // --- Items: format validation only (registries not yet available at load time) ---
        entry.getItemEntries().removeIf(item -> {
            String itemId = item.getId();
            if (!ResourceLocation.isValidResourceLocation(itemId)) {
                addMessage(MessageLevel.WARN, "Item '" + itemId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Items", "Item '" + itemId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Tags ---
        entry.getTags().removeIf(tagId -> {
            if (!ResourceLocation.isValidResourceLocation(tagId)) {
                addMessage(MessageLevel.WARN, "Tag '" + tagId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Tags", "Tag '" + tagId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Mods: format only, missing mods are not removed (optional dependencies) ---
        entry.getMods().removeIf(modId -> {
            if (modId == null || modId.isEmpty() || modId.contains(" ")) {
                addMessage(MessageLevel.WARN, "Mod ID '" + modId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Mods", "Mod ID '" + modId + "' has invalid format (Stage: " + stageId + "). Removed.");
                return true;
            }
            if (!ModList.get().isLoaded(modId)) {
                addMessage(MessageLevel.INFO, "Mod '" + modId + "' not installed (Stage: " + stageId + "). Entry kept.");
                DebugLogger.info("Missing Mods", "Mod '" + modId + "' is not installed (Stage: " + stageId + "). Entry kept — will apply if mod is added later.");
            }
            return false;
        });

        // --- Mod Exceptions: format validation + must belong to a locked mod ---
        Set<String> lockedMods = new HashSet<>(entry.getMods());
        entry.getModExceptionEntries().removeIf(exceptionEntry -> {
            String exItemId = exceptionEntry.getId();
            if (!ResourceLocation.isValidResourceLocation(exItemId)) {
                addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Mod Exceptions", "Mod exception '" + exItemId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            ResourceLocation rl = new ResourceLocation(exItemId);
            if (!lockedMods.contains(rl.getNamespace())) {
                addMessage(MessageLevel.ERROR, "Mod exception '" + exItemId + "' does not belong to a locked mod (Stage: " + stageId + "). Removed.");
                DebugLogger.error("Invalid Mod Exceptions", "Mod exception '" + exItemId + "' belongs to mod '" + rl.getNamespace() + "' which is not in the 'mods' list (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Dimensions ---
        entry.getDimensions().removeIf(dimId -> {
            if (!ResourceLocation.isValidResourceLocation(dimId)) {
                addMessage(MessageLevel.WARN, "Dimension '" + dimId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Dimensions", "Dimension '" + dimId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Structures (plain IDs and "#tag" entries allowed) ---
        entry.getStructures().removeIf(structId -> {
            String check = structId != null && structId.startsWith("#") ? structId.substring(1) : structId;
            if (!ResourceLocation.isValidResourceLocation(check)) {
                addMessage(MessageLevel.WARN, "Structure '" + structId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Structures", "Structure '" + structId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Recipes ---
        entry.getRecipes().removeIf(recipeId -> {
            if (!ResourceLocation.isValidResourceLocation(recipeId)) {
                addMessage(MessageLevel.WARN, "Recipe '" + recipeId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Recipes", "Recipe '" + recipeId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Entities (attacklock) ---
        entry.getEntities().getAttacklock().removeIf(entityId -> {
            if (!ResourceLocation.isValidResourceLocation(entityId)) {
                addMessage(MessageLevel.WARN, "Entity attacklock '" + entityId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Entities", "Entity attacklock '" + entityId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Entities (spawnlock) ---
        entry.getEntities().getSpawnlock().removeIf(entityId -> {
            if (!ResourceLocation.isValidResourceLocation(entityId)) {
                addMessage(MessageLevel.WARN, "Entity spawnlock '" + entityId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Entities", "Entity spawnlock '" + entityId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Redundant entities: in both spawnlock AND attacklock ---
        for (String entityId : entry.getEntities().getSpawnlock()) {
            if (entry.getEntities().getAttacklock().contains(entityId)) {
                addMessage(MessageLevel.INFO, "Entity '" + entityId + "' in both attacklock and spawnlock (Stage: " + stageId + "). Redundant.");
                DebugLogger.info("Redundant Entities", "Entity '" + entityId + "' is in both attacklock and spawnlock (Stage: " + stageId + "). Spawnlock already implies attacklock — the attacklock entry is redundant.");
            }
        }

        // --- Dependencies validation ---
        if (entry.hasDependencies()) {
            for (DependencyGroup group : entry.getDependencies()) {
                // Validate dependency item IDs
                group.getItems().removeIf(depItem -> {
                    if (depItem.getId() == null || !ResourceLocation.isValidResourceLocation(depItem.getId())) {
                        addMessage(MessageLevel.WARN, "Dependency item '" + depItem.getId() + "' invalid format (Stage: " + stageId + "). Removed.");
                        return true;
                    }
                    return false;
                });
                // Validate entity kill IDs
                group.getEntityKills().removeIf(kill -> {
                    if (kill.getEntityId() == null || !ResourceLocation.isValidResourceLocation(kill.getEntityId())) {
                        addMessage(MessageLevel.WARN, "Dependency entity kill '" + kill.getEntityId() + "' invalid format (Stage: " + stageId + "). Removed.");
                        return true;
                    }
                    return false;
                });
                // Validate stat IDs
                group.getStats().removeIf(stat -> {
                    if (stat.getStatId() == null || !ResourceLocation.isValidResourceLocation(stat.getStatId())) {
                        addMessage(MessageLevel.WARN, "Dependency stat '" + stat.getStatId() + "' invalid format (Stage: " + stageId + "). Removed.");
                        return true;
                    }
                    return false;
                });
                // Validate advancement IDs
                group.getAdvancements().removeIf(adv -> {
                    if (adv == null || !ResourceLocation.isValidResourceLocation(adv)) {
                        addMessage(MessageLevel.WARN, "Dependency advancement '" + adv + "' invalid format (Stage: " + stageId + "). Removed.");
                        return true;
                    }
                    return false;
                });
                // Warn about unknown stage references (non-fatal, stage might not be loaded yet)
                for (String depStageId : group.getStages()) {
                    if (!STAGES.containsKey(depStageId)) {
                        addMessage(MessageLevel.INFO, "Dependency stage '" + depStageId + "' not found (Stage: " + stageId + "). May load later.");
                    }
                }
            }
        }

        // --- Research Time ---
        if (entry.getResearchTime() < 0) {
            addMessage(MessageLevel.INFO, "Stage '" + stageId + "' has negative research_time (" + entry.getResearchTime() + "). Using global default.");
            DebugLogger.info("Configuration", "Stage '" + stageId + "' has negative research_time (" + entry.getResearchTime() + "). Falling back to global default.");
        }

        // --- Empty stage check ---
        int totalEntries = entry.getItemEntries().size() + entry.getTags().size() + entry.getMods().size()
                + entry.getModExceptionEntries().size() + entry.getRecipes().size() + entry.getDimensions().size()
                + entry.getEntities().getAttacklock().size() + entry.getEntities().getSpawnlock().size();
        if (totalEntries == 0) {
            addMessage(MessageLevel.INFO, "Stage '" + stageId + "' has no content. It won't lock anything.");
            DebugLogger.info("Empty Stages", "Stage '" + stageId + "' has no content at all. It will be loaded but won't lock anything.");
        }

        STAGES.put(stageId, entry);
        System.out.println("[HistoryStages] Stage geladen: " + stageId);
    }

    private static void removeEmptyItemEntries(List<ItemEntry> list, String stageId) {
        int removed = 0;
        var it = list.iterator();
        while (it.hasNext()) {
            ItemEntry entry = it.next();
            if (entry.getId() == null || entry.getId().isBlank()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            addMessage(MessageLevel.WARN, "Removed " + removed + " empty item(s) from 'items' (Stage: " + stageId + ").");
            DebugLogger.warn("Empty Entries", "Removed " + removed + " empty item(s) from 'items' (Stage: " + stageId + ").");
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

    private static void removeEmptyStrings(List<String> list, String stageId, String field) {
        int removed = 0;
        var it = list.iterator();
        while (it.hasNext()) {
            String val = it.next();
            if (val == null || val.isBlank()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            addMessage(MessageLevel.WARN, "Removed " + removed + " empty string(s) from '" + field + "' (Stage: " + stageId + ").");
            DebugLogger.warn("Empty Entries", "Removed " + removed + " empty/blank string(s) from '" + field + "' (Stage: " + stageId + ").");
        }
    }

    private static void checkDuplicates(List<String> list, String stageId, String field) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String val : list) {
            if (!seen.add(val)) {
                duplicates.add(val);
            }
        }
        if (!duplicates.isEmpty()) {
            for (String dup : duplicates) {
                addMessage(MessageLevel.INFO, "Duplicate '" + dup + "' in '" + field + "' (Stage: " + stageId + ").");
                DebugLogger.info("Duplicates", "Duplicate entry '" + dup + "' in '" + field + "' (Stage: " + stageId + "). Only the first occurrence will be used.");
            }
        }
    }

    public static List<LoadingMessage> getLoadingMessages() {
        return LOADING_MESSAGES;
    }

    public static void reloadStages() {
        load();
    }

    /**
     * Validates stage entries against the actual registries.
     * Must be called AFTER registries are fully loaded (e.g. on world load),
     * NOT during mod construction when load() runs.
     */
    public static void validateAgainstRegistries() {
        for (Map.Entry<String, StageEntry> stageEntry : STAGES.entrySet()) {
            String stageId = stageEntry.getKey();
            StageEntry entry = stageEntry.getValue();

            // Validate items exist in registry
            for (String itemId : entry.getAllItemIds()) {
                if (!ResourceLocation.isValidResourceLocation(itemId)) continue; // already handled by format check
                ResourceLocation rl = new ResourceLocation(itemId);
                if (!ForgeRegistries.ITEMS.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Item '" + itemId + "' does not exist in registry (Stage: " + stageId + ").");
                    DebugLogger.warn("Unknown Items", "Item '" + itemId + "' is a valid ResourceLocation but does not exist in the item registry (Stage: " + stageId + "). Typo or missing mod?");
                }
            }

            // Validate mod exceptions exist in registry
            for (String exItemId : entry.getAllModExceptionIds()) {
                if (!ResourceLocation.isValidResourceLocation(exItemId)) continue;
                ResourceLocation rl = new ResourceLocation(exItemId);
                if (!ForgeRegistries.ITEMS.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' does not exist in registry (Stage: " + stageId + ").");
                    DebugLogger.warn("Unknown Mod Exceptions", "Mod exception '" + exItemId + "' does not exist in the item registry (Stage: " + stageId + "). Typo or missing mod?");
                }
            }

            // Validate entity types exist in registry
            for (String entityId : entry.getEntities().getAttacklock()) {
                if (!ResourceLocation.isValidResourceLocation(entityId)) continue;
                ResourceLocation rl = new ResourceLocation(entityId);
                if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Entity '" + entityId + "' does not exist in registry (Stage: " + stageId + ", attacklock).");
                    DebugLogger.warn("Unknown Entities", "Entity '" + entityId + "' does not exist in the entity registry (Stage: " + stageId + ", attacklock). Typo or missing mod?");
                }
            }
            for (String entityId : entry.getEntities().getSpawnlock()) {
                if (!ResourceLocation.isValidResourceLocation(entityId)) continue;
                ResourceLocation rl = new ResourceLocation(entityId);
                if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Entity '" + entityId + "' does not exist in registry (Stage: " + stageId + ", spawnlock).");
                    DebugLogger.warn("Unknown Entities", "Entity '" + entityId + "' does not exist in the entity registry (Stage: " + stageId + ", spawnlock). Typo or missing mod?");
                }
            }
        }

        // Validate individual stages against registries
        for (Map.Entry<String, StageEntry> indEntry : INDIVIDUAL_STAGES.entrySet()) {
            String indId = indEntry.getKey();
            StageEntry indData = indEntry.getValue();

            for (String itemId : indData.getAllItemIds()) {
                if (!ResourceLocation.isValidResourceLocation(itemId)) continue;
                ResourceLocation rl = new ResourceLocation(itemId);
                if (!ForgeRegistries.ITEMS.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Item '" + itemId + "' does not exist in registry (Individual Stage: " + indId + ").");
                    DebugLogger.warn("Unknown Items", "Item '" + itemId + "' does not exist in the item registry (Individual Stage: " + indId + "). Typo or missing mod?");
                }
            }

            for (String exItemId : indData.getAllModExceptionIds()) {
                if (!ResourceLocation.isValidResourceLocation(exItemId)) continue;
                ResourceLocation rl = new ResourceLocation(exItemId);
                if (!ForgeRegistries.ITEMS.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' does not exist in registry (Individual Stage: " + indId + ").");
                    DebugLogger.warn("Unknown Mod Exceptions", "Mod exception '" + exItemId + "' does not exist in the item registry (Individual Stage: " + indId + "). Typo or missing mod?");
                }
            }

            for (String entityId : indData.getEntities().getAttacklock()) {
                if (!ResourceLocation.isValidResourceLocation(entityId)) continue;
                ResourceLocation rl = new ResourceLocation(entityId);
                if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Entity '" + entityId + "' does not exist in registry (Individual Stage: " + indId + ", attacklock).");
                    DebugLogger.warn("Unknown Entities", "Entity '" + entityId + "' does not exist in the entity registry (Individual Stage: " + indId + ", attacklock). Typo or missing mod?");
                }
            }
        }
    }

    public static Map<String, StageEntry> getStages() {
        return STAGES;
    }

    /**
     * Replaces all stage definitions with the given map.
     * Used on the client side to sync stage definitions from the server in multiplayer.
     */
    public static void setStages(Map<String, StageEntry> stages) {
        STAGES.clear();
        if (stages != null) {
            STAGES.putAll(stages);
        }
    }

    public static String getStageForItemOrMod(String itemId, String modId) {
        for (var entry : STAGES.entrySet()) {
            String stageName = entry.getKey();
            StageEntry data = entry.getValue();

            if (data.getItems() != null && data.getItems().contains(itemId)) return stageName;
            if (data.getMods() != null && data.getMods().contains(modId)
                    && !isModException(itemId, null, data)) return stageName;

            if (data.getTags() != null) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                if (item != null) {
                    for (String tagId : data.getTags()) {
                        var tagKey = net.minecraft.tags.TagKey.create(Registries.ITEM, new ResourceLocation(tagId));
                        if (item.builtInRegistryHolder().is(tagKey)) return stageName;
                    }
                }
            }
        }
        return null;
    }

    public static List<String> getAllStagesForAttackLockedEntity(String entityId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            EntityLocks locks = entry.getValue().getEntities();
            // Spawnlock-Entities sind automatisch auch attacklocked
            if (locks.getAttacklock().contains(entityId) || locks.getSpawnlock().contains(entityId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static List<String> getAllStagesForSpawnLockedEntity(String entityId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            if (entry.getValue().getEntities().getSpawnlock().contains(entityId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static String getStageForDimension(String dimensionId) {
        for (var entry : STAGES.entrySet()) {
            StageEntry data = entry.getValue();
            if (data.getDimensions() != null && data.getDimensions().contains(dimensionId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static List<String> getAllStagesForDimension(String dimensionId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            if (entry.getValue().getDimensions() != null && entry.getValue().getDimensions().contains(dimensionId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static List<String> getAllStagesForStructure(String structureId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            if (entry.getValue().getStructures() != null && entry.getValue().getStructures().contains(structureId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static boolean anyStageHasStructures() {
        for (StageEntry entry : STAGES.values()) {
            if (entry.getStructures() != null && !entry.getStructures().isEmpty()) return true;
        }
        for (StageEntry entry : INDIVIDUAL_STAGES.values()) {
            if (entry.getStructures() != null && !entry.getStructures().isEmpty()) return true;
        }
        return false;
    }

    public static List<String> getAllStagesForItemOrMod(String itemId, String modId) {
        return getAllStagesForItemOrMod(itemId, modId, null);
    }

    public static List<String> getAllStagesForItemOrMod(String itemId, String modId, net.minecraft.world.item.ItemStack stack) {
        List<String> allFoundStages = new ArrayList<>();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));

        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            String stageName = entry.getKey();
            StageEntry data = entry.getValue();

            boolean match = false;
            // Check Item ID (with NBT matching)
            for (ItemEntry itemEntry : data.getItemEntries()) {
                if (itemEntry.getId().equals(itemId)) {
                    if (itemEntry.hasNbt()) {
                        // NBT matching requires an ItemStack
                        if (stack != null && NbtMatcher.matches(stack, itemEntry.getNbt())) {
                            match = true;
                            break;
                        }
                    } else {
                        match = true;
                        break;
                    }
                }
            }
            // Check Mod ID (with exception check)
            if (!match && data.getMods().contains(modId)) {
                // Check if item is in mod exceptions
                if (!isModException(itemId, stack, data)) {
                    match = true;
                }
            }
            // Check Tags
            if (!match && item != null && data.getTags() != null) {
                for (String tagId : data.getTags()) {
                    var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, new ResourceLocation(tagId));
                    if (item.builtInRegistryHolder().is(tagKey)) {
                        match = true;
                        break;
                    }
                }
            }

            if (match) {
                allFoundStages.add(stageName);
            }
        }
        return allFoundStages;
    }

    /** Delegates to StageEntry.isModExcepted for consistency. */
    private static boolean isModException(String itemId, net.minecraft.world.item.ItemStack stack, StageEntry data) {
        return data.isModExcepted(itemId, stack);
    }

    /**
     * Returns the research time in ticks for a stage.
     * Uses the stage's own research_time if > 0, otherwise falls back to the global config.
     */
    public static int getResearchTimeInTicks(String stageId) {
        StageEntry entry = STAGES.get(stageId);
        if (entry != null && entry.getResearchTime() > 0) {
            return entry.getResearchTime() * 20;
        }
        return net.bananemdnsa.historystages.Config.COMMON.researchTimeInSeconds.get() * 20;
    }

    public static boolean saveStage(String stageId, StageEntry entry) {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("global").toFile();
        if (!configDir.exists()) configDir.mkdirs();

        File file = new File(configDir, stageId + ".json");
        try (Writer writer = new FileWriter(file)) {
            writer.write(entry.toJson());
            STAGES.put(stageId, entry);
            DebugLogger.runtime("Stage Save", "Saved stage '" + stageId + "' to " + file.getName());
            return true;
        } catch (Exception e) {
            System.err.println("[HistoryStages] Failed to save stage: " + stageId + " - " + e.getMessage());
            DebugLogger.error("Stage Saving", "Failed to save stage '" + stageId + "': " + e.getMessage());
            DebugLogger.writeLogFile(STAGES, INDIVIDUAL_STAGES);
            return false;
        }
    }

    public static boolean deleteStage(String stageId) {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("global").toFile();
        File file = new File(configDir, stageId + ".json");
        if (file.exists() && file.delete()) {
            STAGES.remove(stageId);
            DebugLogger.runtime("Stage Delete", "Deleted stage '" + stageId + "'");
            return true;
        }
        return false;
    }

    public static List<String> getStageOrder() {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("global").toFile();
        if (!configDir.exists()) return new ArrayList<>(STAGES.keySet());

        File[] files = configDir.listFiles((dir, name) ->
                name.endsWith(".json") && !name.startsWith("_")
        );
        if (files == null) return new ArrayList<>(STAGES.keySet());

        Arrays.sort(files);
        List<String> order = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().replace(".json", "");
            if (STAGES.containsKey(id)) {
                order.add(id);
            }
        }
        // Add any stages not found as files (shouldn't happen, but safety)
        for (String id : STAGES.keySet()) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        return order;
    }

    public static boolean isRecipeIdLockedForServer(String recipeId) {
        return isRecipeIdLocked(recipeId, false);
    }

    /**
     * Checks if a recipe ID is locked — works on Client AND Server.
     * Uses ClientStageCache on the client, SERVER_CACHE on the server.
     */
    public static boolean isRecipeIdLocked(String recipeId, boolean isClientSide) {
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            if (entry.getValue().getRecipes().contains(recipeId)) {
                if (isClientSide) {
                    if (!net.bananemdnsa.historystages.util.ClientStageCache.isStageUnlocked(entry.getKey())) {
                        return true;
                    }
                } else {
                    if (!net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(entry.getKey())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Die zentrale Prüf-Logik für den Server (z.B. Lootr)
    public static boolean isItemLockedForServer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res == null) return false;

        // Wir holen uns ALLE Stages, die für dieses Item registriert sind
        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace(), stack);

        if (requiredStages.isEmpty()) return false;

        // NEUE LOGIK: Das Item ist GESPERRT, wenn mindestens EINE der benötigten Stages FEHLT
        // (Der Spieler muss also ALLE Stages besitzen, um es zu sehen)
        for (String stage : requiredStages) {
            if (!net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(stage)) {
                return true; // Eine Stage fehlt noch -> Item bleibt gesperrt
            }
        }

        return false; // Alle erforderlichen Stages sind im Cache -> Item ist frei
    }

    /**
     * Prüft ob ein Item gesperrt ist — funktioniert auf Client UND Server.
     * Nutzt auf dem Client den ClientStageCache, auf dem Server den SERVER_CACHE.
     */
    public static boolean isItemLocked(ItemStack stack, boolean isClientSide) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res == null) return false;

        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace(), stack);
        if (requiredStages.isEmpty()) return false;

        for (String stage : requiredStages) {
            if (isClientSide) {
                if (!net.bananemdnsa.historystages.util.ClientStageCache.isStageUnlocked(stage)) {
                    return true;
                }
            } else {
                if (!net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(stage)) {
                    return true;
                }
            }
        }
        return false;
    }

    // =============================================
    // INDIVIDUAL STAGES
    // =============================================

    private static final Set<String> INDIVIDUAL_UNSUPPORTED_KEYS = Set.of("recipes");

    private static void loadIndividual() {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("individual").toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
            return;
        }

        File[] files = configDir.listFiles((dir, name) ->
                name.endsWith(".json") && !name.startsWith("_")
        );

        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".json", "");

            validateFileName(id, file.getName());

            try (Reader reader = new FileReader(file)) {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                detectUnknownKeys(id, content);

                StageEntry entry = GSON.fromJson(content, StageEntry.class);

                if (entry != null) {
                    stripUnsupportedIndividualCategories(id, entry);
                    validateAndAddIndividual(id, entry);
                } else {
                    String msg = "Individual file '" + file.getName() + "' parsed as null (empty or invalid JSON)";
                    addMessage(MessageLevel.ERROR, msg);
                    DebugLogger.error("Individual Stage Loading", msg);
                }
            } catch (Exception e) {
                String msg = "Error in individual file: " + file.getName() + " (Invalid JSON syntax, stage skipped)";
                addMessage(MessageLevel.ERROR, msg);
                DebugLogger.error("Individual Stage Loading", msg + " — " + e.getMessage());
            }
        }

        // Overlap detection: individual entries that conflict with global stages are skipped
        detectOverlaps();

        System.out.println("[HistoryStages] Individual Stages geladen: " + INDIVIDUAL_STAGES.size());
    }

    private static void stripUnsupportedIndividualCategories(String stageId, StageEntry entry) {
        // Recipes are not supported for individual stages
        if (entry.getRecipes() != null && !entry.getRecipes().isEmpty()) {
            String msg = "Individual stage '" + stageId + "' contains 'recipes' — not supported for individual stages. Entries removed.";
            addMessage(MessageLevel.ERROR, msg);
            DebugLogger.error("Individual Stage Loading", msg);
            entry.getRecipes().clear();
        }

        // Spawnlock is not supported for individual stages
        if (entry.getEntities().getSpawnlock() != null && !entry.getEntities().getSpawnlock().isEmpty()) {
            String msg = "Individual stage '" + stageId + "' contains 'entities.spawnlock' — not supported for individual stages. Entries removed.";
            addMessage(MessageLevel.ERROR, msg);
            DebugLogger.error("Individual Stage Loading", msg);
            entry.getEntities().getSpawnlock().clear();
        }
    }

    private static void validateAndAddIndividual(String stageId, StageEntry entry) {
        // Check for duplicate stage ID across global and individual
        if (STAGES.containsKey(stageId)) {
            String msg = "Individual stage '" + stageId + "' has the same ID as a global stage. Individual stage skipped.";
            addMessage(MessageLevel.ERROR, msg);
            DebugLogger.error("Individual Stage Loading", msg);
            return;
        }

        // Reuse global validation (empty strings, duplicates, format checks)
        removeEmptyItemEntries(entry.getItemEntries(), stageId);
        removeEmptyStrings(entry.getTags(), stageId, "tags");
        removeEmptyStrings(entry.getMods(), stageId, "mods");
        removeEmptyItemEntries(entry.getModExceptionEntries(), stageId);
        removeEmptyStrings(entry.getDimensions(), stageId, "dimensions");
        removeEmptyStrings(entry.getStructures(), stageId, "structures");
        removeEmptyStrings(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");

        checkDuplicateItems(entry.getItemEntries(), stageId);
        checkDuplicates(entry.getTags(), stageId, "tags");
        checkDuplicates(entry.getMods(), stageId, "mods");
        checkDuplicateItems(entry.getModExceptionEntries(), stageId);
        checkDuplicates(entry.getDimensions(), stageId, "dimensions");
        checkDuplicates(entry.getStructures(), stageId, "structures");
        checkDuplicates(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");

        entry.getItemEntries().removeIf(item -> {
            if (!ResourceLocation.isValidResourceLocation(item.getId())) {
                addMessage(MessageLevel.WARN, "Item '" + item.getId() + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getTags().removeIf(tagId -> {
            if (!ResourceLocation.isValidResourceLocation(tagId)) {
                addMessage(MessageLevel.WARN, "Tag '" + tagId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getMods().removeIf(modId -> {
            if (modId == null || modId.isEmpty() || modId.contains(" ")) {
                addMessage(MessageLevel.WARN, "Mod ID '" + modId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Mod Exceptions: format validation + must belong to a locked mod ---
        Set<String> indLockedMods = new HashSet<>(entry.getMods());
        entry.getModExceptionEntries().removeIf(exceptionEntry -> {
            String exItemId = exceptionEntry.getId();
            if (!ResourceLocation.isValidResourceLocation(exItemId)) {
                addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            ResourceLocation rl = new ResourceLocation(exItemId);
            if (!indLockedMods.contains(rl.getNamespace())) {
                addMessage(MessageLevel.ERROR, "Mod exception '" + exItemId + "' does not belong to a locked mod (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getDimensions().removeIf(dimId -> {
            if (!ResourceLocation.isValidResourceLocation(dimId)) {
                addMessage(MessageLevel.WARN, "Dimension '" + dimId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getStructures().removeIf(structId -> {
            String check = structId != null && structId.startsWith("#") ? structId.substring(1) : structId;
            if (!ResourceLocation.isValidResourceLocation(check)) {
                addMessage(MessageLevel.WARN, "Structure '" + structId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getEntities().getAttacklock().removeIf(entityId -> {
            if (!ResourceLocation.isValidResourceLocation(entityId)) {
                addMessage(MessageLevel.WARN, "Entity attacklock '" + entityId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        if (entry.getDisplayName().equals("Unknown Stage")) {
            addMessage(MessageLevel.WARN, "Individual stage '" + stageId + "' has no 'display_name'. Defaults to 'Unknown Stage'.");
        }

        if (entry.getResearchTime() < 0) {
            addMessage(MessageLevel.INFO, "Individual stage '" + stageId + "' has negative research_time. Using global default.");
        }

        INDIVIDUAL_STAGES.put(stageId, entry);
        System.out.println("[HistoryStages] Individual Stage geladen: " + stageId);
    }

    /**
     * Detects overlaps between individual and global stages.
     * Overlapping items/tags/mods are registered as dual-phase entries instead of being removed.
     * Dual-phase: first locked globally (all paired global stages must be unlocked), then locked per-player.
     * Note: Dimensions and entities are allowed to overlap — a player needs BOTH global and individual stages unlocked.
     */
    private static void detectOverlaps() {
        // Build a lookup: item/tag/mod -> set of all global stage IDs containing it
        Map<String, Set<String>> globalItemMap = new HashMap<>();
        Map<String, Set<String>> globalTagMap = new HashMap<>();
        Map<String, Set<String>> globalModMap = new HashMap<>();

        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            String gStageId = entry.getKey();
            StageEntry gEntry = entry.getValue();
            for (String item : gEntry.getAllItemIds())
                globalItemMap.computeIfAbsent(item, k -> new HashSet<>()).add(gStageId);
            for (String tag : gEntry.getTags())
                globalTagMap.computeIfAbsent(tag, k -> new HashSet<>()).add(gStageId);
            for (String mod : gEntry.getMods())
                globalModMap.computeIfAbsent(mod, k -> new HashSet<>()).add(gStageId);
        }

        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            String iStageId = entry.getKey();
            StageEntry iEntry = entry.getValue();

            for (ItemEntry itemEntry : iEntry.getItemEntries()) {
                Set<String> globalStages = globalItemMap.get(itemEntry.getId());
                if (globalStages != null) {
                    DUAL_PHASE_ITEMS.computeIfAbsent(itemEntry.getId(), k -> new HashSet<>()).addAll(globalStages);
                    String msg = "Individual stage '" + iStageId + "' item '" + itemEntry.getId() + "' also in global stage(s) " + globalStages + " — dual-phase lock registered.";
                    addMessage(MessageLevel.INFO, msg);
                    DebugLogger.info("Dual-Phase Detection", msg);
                }
            }

            for (String tag : iEntry.getTags()) {
                Set<String> globalStages = globalTagMap.get(tag);
                if (globalStages != null) {
                    DUAL_PHASE_TAGS.computeIfAbsent(tag, k -> new HashSet<>()).addAll(globalStages);
                    String msg = "Individual stage '" + iStageId + "' tag '" + tag + "' also in global stage(s) " + globalStages + " — dual-phase lock registered.";
                    addMessage(MessageLevel.INFO, msg);
                    DebugLogger.info("Dual-Phase Detection", msg);
                }
            }

            for (String mod : iEntry.getMods()) {
                Set<String> globalStages = globalModMap.get(mod);
                if (globalStages != null) {
                    DUAL_PHASE_MODS.computeIfAbsent(mod, k -> new HashSet<>()).addAll(globalStages);
                    String msg = "Individual stage '" + iStageId + "' mod '" + mod + "' also in global stage(s) " + globalStages + " — dual-phase lock registered.";
                    addMessage(MessageLevel.INFO, msg);
                    DebugLogger.info("Dual-Phase Detection", msg);
                }
            }
        }
    }

    public static Map<String, StageEntry> getIndividualStages() {
        return INDIVIDUAL_STAGES;
    }

    public static Map<String, Set<String>> getDualPhaseItems() { return DUAL_PHASE_ITEMS; }
    public static Map<String, Set<String>> getDualPhaseTags()  { return DUAL_PHASE_TAGS; }
    public static Map<String, Set<String>> getDualPhaseMods()  { return DUAL_PHASE_MODS; }

    public static void setIndividualStages(Map<String, StageEntry> stages) {
        INDIVIDUAL_STAGES.clear();
        if (stages != null) {
            INDIVIDUAL_STAGES.putAll(stages);
        }
    }

    public static List<String> getAllIndividualStagesForItemOrMod(String itemId, String modId) {
        return getAllIndividualStagesForItemOrMod(itemId, modId, null);
    }

    public static List<String> getAllIndividualStagesForItemOrMod(String itemId, String modId, net.minecraft.world.item.ItemStack stack) {
        List<String> allFoundStages = new ArrayList<>();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));

        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            String stageName = entry.getKey();
            StageEntry data = entry.getValue();

            boolean match = false;
            for (ItemEntry itemEntry : data.getItemEntries()) {
                if (itemEntry.getId().equals(itemId)) {
                    if (itemEntry.hasNbt()) {
                        if (stack != null && NbtMatcher.matches(stack, itemEntry.getNbt())) {
                            match = true;
                            break;
                        }
                    } else {
                        match = true;
                        break;
                    }
                }
            }
            // Check Mod ID (with exception check)
            if (!match && data.getMods().contains(modId)) {
                if (!isModException(itemId, stack, data)) {
                    match = true;
                }
            }
            if (!match && item != null && data.getTags() != null) {
                for (String tagId : data.getTags()) {
                    var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, new ResourceLocation(tagId));
                    if (item.builtInRegistryHolder().is(tagKey)) {
                        match = true;
                        break;
                    }
                }
            }

            if (match) {
                allFoundStages.add(stageName);
            }
        }
        return allFoundStages;
    }

    public static List<String> getAllIndividualStagesForAttackLockedEntity(String entityId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            if (entry.getValue().getEntities().getAttacklock().contains(entityId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static List<String> getAllIndividualStagesForDimension(String dimensionId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            if (entry.getValue().getDimensions() != null && entry.getValue().getDimensions().contains(dimensionId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static List<String> getAllIndividualStagesForStructure(String structureId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            if (entry.getValue().getStructures() != null && entry.getValue().getStructures().contains(structureId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static boolean saveIndividualStage(String stageId, StageEntry entry) {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("individual").toFile();
        if (!configDir.exists()) configDir.mkdirs();

        File file = new File(configDir, stageId + ".json");
        try (Writer writer = new FileWriter(file)) {
            writer.write(entry.toJson());
            INDIVIDUAL_STAGES.put(stageId, entry);
            DebugLogger.runtime("Individual Stage Save", "Saved individual stage '" + stageId + "' to " + file.getName());
            return true;
        } catch (Exception e) {
            System.err.println("[HistoryStages] Failed to save individual stage: " + stageId + " - " + e.getMessage());
            DebugLogger.error("Individual Stage Saving", "Failed to save individual stage '" + stageId + "': " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteIndividualStage(String stageId) {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("individual").toFile();
        File file = new File(configDir, stageId + ".json");
        if (file.exists() && file.delete()) {
            INDIVIDUAL_STAGES.remove(stageId);
            DebugLogger.runtime("Individual Stage Delete", "Deleted individual stage '" + stageId + "'");
            return true;
        }
        return false;
    }

    public static List<String> getIndividualStageOrder() {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("individual").toFile();
        if (!configDir.exists()) return new ArrayList<>(INDIVIDUAL_STAGES.keySet());

        File[] files = configDir.listFiles((dir, name) ->
                name.endsWith(".json") && !name.startsWith("_")
        );
        if (files == null) return new ArrayList<>(INDIVIDUAL_STAGES.keySet());

        Arrays.sort(files);
        List<String> order = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().replace(".json", "");
            if (INDIVIDUAL_STAGES.containsKey(id)) {
                order.add(id);
            }
        }
        for (String id : INDIVIDUAL_STAGES.keySet()) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        return order;
    }

    /**
     * Returns the research time in ticks for an individual stage.
     * Falls back to global config if stage has no custom time.
     */
    public static int getIndividualResearchTimeInTicks(String stageId) {
        StageEntry entry = INDIVIDUAL_STAGES.get(stageId);
        if (entry != null && entry.getResearchTime() > 0) {
            return entry.getResearchTime() * 20;
        }
        return net.bananemdnsa.historystages.Config.COMMON.researchTimeInSeconds.get() * 20;
    }

    /**
     * Checks if a stage ID belongs to an individual stage.
     */
    public static boolean isIndividualStage(String stageId) {
        return INDIVIDUAL_STAGES.containsKey(stageId);
    }
}