package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StageManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, StageEntry> STAGES = new HashMap<>();
    private static final Map<String, StageEntry> INDIVIDUAL_STAGES = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_ITEMS = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_TAGS = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_MODS = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_DIMENSIONS = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_STRUCTURES = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_ATTACKLOCK = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_ITEMS_IND = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_TAGS_IND = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_MODS_IND = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_DIMENSIONS_IND = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_STRUCTURES_IND = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_ATTACKLOCK_IND = new HashMap<>();
    private static final List<LoadingMessage> LOADING_MESSAGES = new ArrayList<>();
    private static final Set<String> KNOWN_KEYS = Set.of(
            "display_name", "research_time", "icon", "items", "tags", "mods",
            "mod_exceptions", "recipes", "dimensions", "structures", "entities", "dependencies");
    private static final Set<String> KNOWN_ENTITY_KEYS = Set.of("spawnlock", "attacklock", "modLinked");
    private static final Set<String> KNOWN_LOCK_ACTIONS = Set.of(
            "equip", "attack", "place", "break", "pickup", "use", "loot", "recipe", "gui", "icon");

    private StageManager() {
    }

    public enum MessageLevel { ERROR, WARN, INFO }

    public record LoadingMessage(MessageLevel level, String message) {
    }

    public static void load() {
        STAGES.clear();
        INDIVIDUAL_STAGES.clear();
        clearDualPhase();
        LOADING_MESSAGES.clear();
        DebugLogger.clear();
        DebugLogger.ensureLogDirectory();

        loadDirectory(globalDir(), STAGES, false);
        loadDirectory(individualDir(), INDIVIDUAL_STAGES, true);
        detectOverlaps();
        checkCircularDependencies();
        DebugLogger.setStagesLoaded(STAGES.size() + INDIVIDUAL_STAGES.size());
        DebugLogger.writeDiagnosticReport(STAGES, INDIVIDUAL_STAGES, LOADING_MESSAGES);
    }

    public static void reloadStages() {
        load();
    }

    public static Map<String, StageEntry> getStages() {
        return STAGES;
    }

    public static Map<String, StageEntry> getIndividualStages() {
        return INDIVIDUAL_STAGES;
    }

    public static Map<String, Set<String>> getDualPhaseItems() {
        return DUAL_PHASE_ITEMS;
    }

    public static Map<String, Set<String>> getDualPhaseTags() {
        return DUAL_PHASE_TAGS;
    }

    public static Map<String, Set<String>> getDualPhaseMods() {
        return DUAL_PHASE_MODS;
    }

    public static Map<String, Set<String>> getDualPhaseDimensions() {
        return DUAL_PHASE_DIMENSIONS;
    }

    public static Map<String, Set<String>> getDualPhaseStructures() {
        return DUAL_PHASE_STRUCTURES;
    }

    public static Map<String, Set<String>> getDualPhaseAttacklock() {
        return DUAL_PHASE_ATTACKLOCK;
    }

    public static Map<String, Set<String>> getDualPhaseItemsInd() {
        return DUAL_PHASE_ITEMS_IND;
    }

    public static Map<String, Set<String>> getDualPhaseTagsInd() {
        return DUAL_PHASE_TAGS_IND;
    }

    public static Map<String, Set<String>> getDualPhaseModsInd() {
        return DUAL_PHASE_MODS_IND;
    }

    public static Map<String, Set<String>> getDualPhaseDimensionsInd() {
        return DUAL_PHASE_DIMENSIONS_IND;
    }

    public static Map<String, Set<String>> getDualPhaseStructuresInd() {
        return DUAL_PHASE_STRUCTURES_IND;
    }

    public static Map<String, Set<String>> getDualPhaseAttacklockInd() {
        return DUAL_PHASE_ATTACKLOCK_IND;
    }

    public static List<String> getStageOrder() {
        return STAGES.keySet().stream().sorted().toList();
    }

    public static List<String> getIndividualStageOrder() {
        return INDIVIDUAL_STAGES.keySet().stream().sorted().toList();
    }

    public static String serializeStages(Map<String, StageEntry> stages) {
        return GSON.toJson(stages);
    }

    public static void applySyncedDefinitions(String globalStagesJson, String individualStagesJson) {
        STAGES.clear();
        STAGES.putAll(deserializeStages(globalStagesJson));
        INDIVIDUAL_STAGES.clear();
        INDIVIDUAL_STAGES.putAll(deserializeStages(individualStagesJson));
        rebuildDualPhase();
    }

    public static List<LoadingMessage> getLoadingMessages() {
        return List.copyOf(LOADING_MESSAGES);
    }

    public static boolean isItemLockedForServer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        for (String stageId : getAllStagesForItemOrMod(id.toString(), id.getNamespace(), stack)) {
            if (!net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(stageId)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isItemLocked(ItemStack stack, boolean isClientSide) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        for (String stageId : getAllStagesForItemOrMod(id.toString(), id.getNamespace(), stack)) {
            boolean unlocked = isClientSide
                    ? net.bananemdnsa.historystages.util.ClientStageCache.isStageUnlocked(stageId)
                    : net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(stageId);
            if (!unlocked) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getAllStagesForItemOrMod(String itemId, String modId, ItemStack stack) {
        return collectStagesForItem(STAGES, itemId, modId, stack);
    }

    public static List<String> getAllIndividualStagesForItemOrMod(String itemId, String modId, ItemStack stack) {
        return collectStagesForItem(INDIVIDUAL_STAGES, itemId, modId, stack);
    }

    public static List<String> getAllStagesForDimension(String dimensionId) {
        return collectStages(STAGES, entry -> entry.getDimensions().contains(dimensionId));
    }

    public static List<String> getAllIndividualStagesForDimension(String dimensionId) {
        return collectStages(INDIVIDUAL_STAGES, entry -> entry.getDimensions().contains(dimensionId));
    }

    public static List<String> getAllStagesForAttackLockedEntity(String entityId) {
        return collectStages(STAGES, entry ->
                entry.getEntities().getAttacklock().contains(entityId)
                        || entry.getEntities().getSpawnlock().contains(entityId));
    }

    public static List<String> getAllStagesForSpawnLockedEntity(String entityId) {
        return collectStages(STAGES, entry -> entry.getEntities().getSpawnlock().contains(entityId));
    }

    public static List<String> getAllIndividualStagesForSpawnLockedEntity(String entityId) {
        return collectStages(INDIVIDUAL_STAGES, entry -> entry.getEntities().getSpawnlock().contains(entityId));
    }

    public static boolean anyStageHasStructures() {
        return STAGES.values().stream().anyMatch(entry -> !entry.getStructures().isEmpty())
                || INDIVIDUAL_STAGES.values().stream().anyMatch(entry -> !entry.getStructures().isEmpty());
    }

    public static boolean isRecipeIdLocked(String recipeId, boolean isClientSide) {
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            if (entry.getValue().getRecipes().contains(recipeId)) {
                boolean unlocked = isClientSide
                        ? net.bananemdnsa.historystages.util.ClientStageCache.isStageUnlocked(entry.getKey())
                        : net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(entry.getKey());
                if (!unlocked) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<String> getAllIndividualStagesForAttackLockedEntity(String entityId) {
        return collectStages(INDIVIDUAL_STAGES, entry ->
                entry.getEntities().getAttacklock().contains(entityId));
    }

    public static boolean saveStage(String stageId, StageEntry entry, boolean individual) {
        Path file = (individual ? individualDir() : globalDir()).resolve(stageId + ".json");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(entry.toJson());
            }
            (individual ? INDIVIDUAL_STAGES : STAGES).put(stageId, entry);
            rebuildDualPhase();
            return true;
        } catch (IOException exception) {
            addMessage(MessageLevel.ERROR, "Failed to save stage '" + stageId + "': " + exception.getMessage());
            return false;
        }
    }

    public static boolean deleteStage(String stageId, boolean individual) {
        Path file = (individual ? individualDir() : globalDir()).resolve(stageId + ".json");
        try {
            Files.deleteIfExists(file);
            (individual ? INDIVIDUAL_STAGES : STAGES).remove(stageId);
            rebuildDualPhase();
            return true;
        } catch (IOException exception) {
            addMessage(MessageLevel.ERROR, "Failed to delete stage '" + stageId + "': " + exception.getMessage());
            return false;
        }
    }

    public static void validateAgainstRegistries() {
        validateEntries(STAGES, "global");
        validateEntries(INDIVIDUAL_STAGES, "individual");
        DebugLogger.writeDiagnosticReport(STAGES, INDIVIDUAL_STAGES, LOADING_MESSAGES);
    }

    public static int getResearchTimeInTicks(String stageId) {
        StageEntry entry = STAGES.get(stageId);
        if (entry != null && entry.getResearchTime() > 0) {
            return entry.getResearchTime() * 20;
        }
        return net.bananemdnsa.historystages.Config.COMMON.researchTimeInSeconds * 20;
    }

    public static int getIndividualResearchTimeInTicks(String stageId) {
        StageEntry entry = INDIVIDUAL_STAGES.get(stageId);
        if (entry != null && entry.getResearchTime() > 0) {
            return entry.getResearchTime() * 20;
        }
        return net.bananemdnsa.historystages.Config.COMMON.researchTimeInSeconds * 20;
    }

    public static boolean isIndividualStage(String stageId) {
        return INDIVIDUAL_STAGES.containsKey(stageId);
    }

    private static void validateEntries(Map<String, StageEntry> stages, String label) {
        for (Map.Entry<String, StageEntry> stage : stages.entrySet()) {
            for (ItemEntry itemEntry : stage.getValue().getItemEntries()) {
                ResourceLocation id = ResourceLocation.tryParse(itemEntry.getId());
                if (id != null && !BuiltInRegistries.ITEM.containsKey(id)) {
                    addMessage(MessageLevel.WARN, "Unknown item '" + itemEntry.getId() + "' in " + label + " stage '" + stage.getKey() + "'.");
                }
                validateLockActions(itemEntry.getLockActions(), stage.getKey(), itemEntry.getId(), "items");
            }
            for (NamedLockEntry tagEntry : stage.getValue().getTagEntries()) {
                validateLockActions(tagEntry.getLockActions(), stage.getKey(), tagEntry.getId(), "tags");
            }
            for (NamedLockEntry modEntry : stage.getValue().getModEntries()) {
                validateLockActions(modEntry.getLockActions(), stage.getKey(), modEntry.getId(), "mods");
            }
        }
    }

    private static List<String> collectStagesForItem(Map<String, StageEntry> source, String itemId, String modId, ItemStack stack) {
        List<String> stages = new ArrayList<>();
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));

        for (Map.Entry<String, StageEntry> entry : source.entrySet()) {
            StageEntry stage = entry.getValue();
            boolean match = false;

            for (ItemEntry stageItem : stage.getItemEntries()) {
                if (stageItem.getId().equals(itemId)) {
                    match = !stageItem.hasNbt() || (stack != null && NbtMatcher.matches(stack, stageItem.getNbt()));
                    if (match) {
                        break;
                    }
                }
            }

            if (!match) {
                for (NamedLockEntry modEntry : stage.getModEntries()) {
                    if (modEntry.getId().equals(modId) && !stage.isModExcepted(itemId, stack)) {
                        match = true;
                        break;
                    }
                }
            }

            if (!match && item != null) {
                for (NamedLockEntry tagEntry : stage.getTagEntries()) {
                    ResourceLocation tagLocation = ResourceLocation.tryParse(tagEntry.getId());
                    if (tagLocation != null && item.builtInRegistryHolder().is(TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagLocation))) {
                        match = true;
                        break;
                    }
                }
            }

            if (match) {
                stages.add(entry.getKey());
            }
        }

        return stages;
    }

    public static boolean isItemActionLockedForStage(String itemId, String modId, ItemStack stack, String action, StageEntry stage) {
        Item item = stack != null && !stack.isEmpty() ? stack.getItem() : BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));

        for (ItemEntry entry : stage.getItemEntries()) {
            if (!entry.getId().equals(itemId)) {
                continue;
            }
            if (!entry.hasNbt() || (stack != null && NbtMatcher.matches(stack, entry.getNbt()))) {
                return isActionLocked(entry.getLockActions(), action);
            }
        }

        for (NamedLockEntry modEntry : stage.getModEntries()) {
            if (modEntry.getId().equals(modId) && !stage.isModExcepted(itemId, stack)) {
                return isActionLocked(modEntry.getLockActions(), action);
            }
        }

        if (item != null) {
            for (NamedLockEntry tagEntry : stage.getTagEntries()) {
                ResourceLocation tagLocation = ResourceLocation.tryParse(tagEntry.getId());
                if (tagLocation != null && item.builtInRegistryHolder().is(TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagLocation))) {
                    return isActionLocked(tagEntry.getLockActions(), action);
                }
            }
        }

        return false;
    }

    private static boolean isActionLocked(List<String> lockActions, String action) {
        return lockActions == null || lockActions.contains(action);
    }

    private static void validateLockActions(List<String> actions, String stageId, String entryId, String fieldPath) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String action : actions) {
            if (action == null || !KNOWN_LOCK_ACTIONS.contains(action)) {
                addMessage(MessageLevel.WARN, "Unknown unlock_action '" + action + "' on '" + entryId + "' in " + fieldPath + " (Stage: " + stageId + ").");
                continue;
            }
            if (!seen.add(action)) {
                DebugLogger.info("Duplicates", "Duplicate unlock_action '" + action + "' on '" + entryId + "' in " + fieldPath + " (Stage: " + stageId + ").");
            }
        }
    }

    private static List<String> collectStages(Map<String, StageEntry> source, java.util.function.Predicate<StageEntry> predicate) {
        List<String> results = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : source.entrySet()) {
            if (predicate.test(entry.getValue())) {
                results.add(entry.getKey());
            }
        }
        return results;
    }

    private static void loadDirectory(Path dir, Map<String, StageEntry> target, boolean individual) {
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(path -> !path.getFileName().toString().startsWith("_"))
                        .sorted()
                        .forEach(path -> loadStageFile(path, target, individual));
            }
        } catch (IOException exception) {
            addMessage(MessageLevel.ERROR, "Failed to read " + dir + ": " + exception.getMessage());
        }
    }

    private static void loadStageFile(Path file, Map<String, StageEntry> target, boolean individual) {
        String stageId = file.getFileName().toString().replace(".json", "");
        validateFileName(stageId, file.getFileName().toString());

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            detectUnknownKeys(stageId, content);

            StageEntry entry = GSON.fromJson(content, StageEntry.class);
            if (entry == null) {
                addMessage(MessageLevel.ERROR, "Stage file '" + file.getFileName() + "' parsed as null.");
                return;
            }

            if (individual) {
                stripUnsupportedIndividualCategories(stageId, entry);
            }

            sanitizeEntry(stageId, entry, individual);
            target.put(stageId, entry);
        } catch (Exception exception) {
            addMessage(MessageLevel.ERROR, "Error in file '" + file.getFileName() + "': " + exception.getMessage());
        }
    }

    private static void sanitizeEntry(String stageId, StageEntry entry, boolean individual) {
        removeEmptyItemEntries(entry.getItemEntries(), stageId);
        removeEmptyStrings(entry.getTags(), stageId, "tags");
        removeEmptyStrings(entry.getMods(), stageId, "mods");
        removeEmptyItemEntries(entry.getModExceptionEntries(), stageId);
        removeEmptyStrings(entry.getRecipes(), stageId, "recipes");
        removeEmptyStrings(entry.getDimensions(), stageId, "dimensions");
        removeEmptyStrings(entry.getStructures(), stageId, "structures");
        removeEmptyStrings(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");
        removeEmptyStrings(entry.getEntities().getSpawnlock(), stageId, "entities.spawnlock");

        entry.getItemEntries().removeIf(item -> !isValidResource(item.getId(), "item", stageId));
        entry.getTags().removeIf(tag -> !isValidResource(tag, "tag", stageId));
        entry.getRecipes().removeIf(recipe -> !isValidResource(recipe, "recipe", stageId));
        entry.getDimensions().removeIf(dimension -> !isValidResource(dimension, "dimension", stageId));
        entry.getEntities().getAttacklock().removeIf(entity -> !isValidResource(entity, "entity.attacklock", stageId));
        entry.getEntities().getSpawnlock().removeIf(entity -> !isValidResource(entity, "entity.spawnlock", stageId));
        entry.getStructures().removeIf(structure -> {
            String check = structure != null && structure.startsWith("#") ? structure.substring(1) : structure;
            return !isValidResource(check, "structure", stageId);
        });

        entry.getMods().removeIf(modId -> {
            boolean invalid = modId == null || modId.isBlank() || modId.contains(" ");
            if (invalid) {
                addMessage(MessageLevel.WARN, "Mod id '" + modId + "' invalid format (Stage: " + stageId + "). Removed.");
                return true;
            }
            if (!FabricLoader.getInstance().isModLoaded(modId)) {
                addMessage(MessageLevel.INFO, "Mod '" + modId + "' not installed (Stage: " + stageId + "). Entry kept.");
            }
            return false;
        });

        Set<String> lockedMods = new HashSet<>(entry.getMods());
        entry.getModExceptionEntries().removeIf(exceptionEntry -> {
            ResourceLocation id = ResourceLocation.tryParse(exceptionEntry.getId());
            if (id == null) {
                addMessage(MessageLevel.WARN, "Mod exception '" + exceptionEntry.getId() + "' invalid format (Stage: " + stageId + "). Removed.");
                return true;
            }
            if (!lockedMods.contains(id.getNamespace())) {
                addMessage(MessageLevel.ERROR, "Mod exception '" + exceptionEntry.getId() + "' does not belong to a locked mod (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        if (entry.getDisplayName().equals("Unknown Stage")) {
            addMessage(MessageLevel.WARN, (individual ? "Individual s" : "S") + "tage '" + stageId + "' has no display_name.");
        }
    }

    private static boolean isValidResource(String value, String kind, String stageId) {
        if (ResourceLocation.tryParse(value) == null) {
            addMessage(MessageLevel.WARN, kind + " '" + value + "' invalid format (Stage: " + stageId + "). Removed.");
            return false;
        }
        return true;
    }

    private static void stripUnsupportedIndividualCategories(String stageId, StageEntry entry) {
        if (!entry.getRecipes().isEmpty()) {
            addMessage(MessageLevel.WARN, "Individual stage '" + stageId + "' contains recipes. These are not ported yet.");
            entry.getRecipes().clear();
        }
        if (!entry.getEntities().getSpawnlock().isEmpty()) {
            addMessage(MessageLevel.WARN, "Individual stage '" + stageId + "' contains entities.spawnlock. These are not ported yet.");
            entry.getEntities().getSpawnlock().clear();
        }
    }

    private static void detectOverlaps() {
        Map<String, Set<String>> globalItems = new HashMap<>();
        Map<String, Set<String>> globalTags = new HashMap<>();
        Map<String, Set<String>> globalMods = new HashMap<>();
        Map<String, Set<String>> globalDimensions = new HashMap<>();
        Map<String, Set<String>> globalStructures = new HashMap<>();
        Map<String, Set<String>> globalAttacklock = new HashMap<>();

        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            String stageId = entry.getKey();
            StageEntry stage = entry.getValue();
            for (String item : stage.getAllItemIds()) {
                globalItems.computeIfAbsent(item, ignored -> new HashSet<>()).add(stageId);
            }
            for (String tag : stage.getTags()) {
                globalTags.computeIfAbsent(tag, ignored -> new HashSet<>()).add(stageId);
            }
            for (String mod : stage.getMods()) {
                globalMods.computeIfAbsent(mod, ignored -> new HashSet<>()).add(stageId);
            }
            for (String dimension : stage.getDimensions()) {
                globalDimensions.computeIfAbsent(dimension, ignored -> new HashSet<>()).add(stageId);
            }
            for (String structure : stage.getStructures()) {
                globalStructures.computeIfAbsent(structure, ignored -> new HashSet<>()).add(stageId);
            }
            for (String entity : stage.getEntities().getAttacklock()) {
                globalAttacklock.computeIfAbsent(entity, ignored -> new HashSet<>()).add(stageId);
            }
            for (String entity : stage.getEntities().getSpawnlock()) {
                globalAttacklock.computeIfAbsent(entity, ignored -> new HashSet<>()).add(stageId);
            }
        }

        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            String stageId = entry.getKey();
            StageEntry stage = entry.getValue();
            for (ItemEntry item : stage.getItemEntries()) {
                registerDualPhase(DUAL_PHASE_ITEMS, DUAL_PHASE_ITEMS_IND, globalItems, item.getId(), "item", stageId);
            }
            for (String tag : stage.getTags()) {
                registerDualPhase(DUAL_PHASE_TAGS, DUAL_PHASE_TAGS_IND, globalTags, tag, "tag", stageId);
            }
            for (String mod : stage.getMods()) {
                registerDualPhase(DUAL_PHASE_MODS, DUAL_PHASE_MODS_IND, globalMods, mod, "mod", stageId);
            }
            for (String dimension : stage.getDimensions()) {
                registerDualPhase(DUAL_PHASE_DIMENSIONS, DUAL_PHASE_DIMENSIONS_IND, globalDimensions, dimension, "dimension", stageId);
            }
            for (String structure : stage.getStructures()) {
                registerDualPhase(DUAL_PHASE_STRUCTURES, DUAL_PHASE_STRUCTURES_IND, globalStructures, structure, "structure", stageId);
            }
            for (String entity : stage.getEntities().getAttacklock()) {
                registerDualPhase(DUAL_PHASE_ATTACKLOCK, DUAL_PHASE_ATTACKLOCK_IND, globalAttacklock, entity, "attacklock entity", stageId);
            }
        }
    }

    public static void rebuildDualPhase() {
        clearDualPhase();
        detectOverlaps();
    }

    private static void clearDualPhase() {
        DUAL_PHASE_ITEMS.clear();
        DUAL_PHASE_TAGS.clear();
        DUAL_PHASE_MODS.clear();
        DUAL_PHASE_DIMENSIONS.clear();
        DUAL_PHASE_STRUCTURES.clear();
        DUAL_PHASE_ATTACKLOCK.clear();
        DUAL_PHASE_ITEMS_IND.clear();
        DUAL_PHASE_TAGS_IND.clear();
        DUAL_PHASE_MODS_IND.clear();
        DUAL_PHASE_DIMENSIONS_IND.clear();
        DUAL_PHASE_STRUCTURES_IND.clear();
        DUAL_PHASE_ATTACKLOCK_IND.clear();
    }

    private static void registerDualPhase(Map<String, Set<String>> globalTarget, Map<String, Set<String>> individualTarget,
                                          Map<String, Set<String>> globalLookup, String entryId, String label,
                                          String individualStageId) {
        Set<String> globalStages = globalLookup.get(entryId);
        if (globalStages == null) {
            return;
        }
        globalTarget.computeIfAbsent(entryId, ignored -> new HashSet<>()).addAll(globalStages);
        individualTarget.computeIfAbsent(entryId, ignored -> new HashSet<>()).add(individualStageId);
        addMessage(MessageLevel.INFO, "Individual stage '" + individualStageId + "' " + label + " '" + entryId
                + "' also in global stage(s) " + globalStages + " - dual-phase lock registered.");
    }

    private static void checkCircularDependencies() {
        Map<String, Set<String>> graph = new HashMap<>();
        addDependencyGraph(graph, STAGES);
        addDependencyGraph(graph, INDIVIDUAL_STAGES);

        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        for (String node : graph.keySet()) {
            if (!visited.contains(node) && hasCycle(node, graph, visited, stack, new ArrayList<>())) {
                return;
            }
        }
    }

    private static void addDependencyGraph(Map<String, Set<String>> graph, Map<String, StageEntry> stages) {
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            Set<String> refs = new HashSet<>();
            for (DependencyGroup group : entry.getValue().getDependencies()) {
                refs.addAll(group.getReferencedStageIds());
            }
            if (!refs.isEmpty()) {
                graph.put(entry.getKey(), refs);
            }
        }
    }

    private static boolean hasCycle(String node, Map<String, Set<String>> graph, Set<String> visited,
                                    Set<String> stack, List<String> path) {
        visited.add(node);
        stack.add(node);
        path.add(node);
        for (String next : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(next) && hasCycle(next, graph, visited, stack, path)) {
                return true;
            }
            if (stack.contains(next)) {
                path.add(next);
                addMessage(MessageLevel.ERROR, "Circular dependency detected: " + String.join(" -> ", path));
                return true;
            }
        }
        stack.remove(node);
        path.remove(path.size() - 1);
        return false;
    }

    private static void detectUnknownKeys(String stageId, String content) {
        try {
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            for (String key : json.keySet()) {
                if (!KNOWN_KEYS.contains(key)) {
                    addMessage(MessageLevel.WARN, "Unknown key '" + key + "' in stage '" + stageId + "'.");
                }
            }
            if (json.has("entities") && json.get("entities").isJsonObject()) {
                JsonObject entities = json.getAsJsonObject("entities");
                for (String key : entities.keySet()) {
                    if (!KNOWN_ENTITY_KEYS.contains(key)) {
                        addMessage(MessageLevel.WARN, "Unknown entity key '" + key + "' in stage '" + stageId + "'.");
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void validateFileName(String id, String fileName) {
        if (!id.equals(id.toLowerCase())) {
            addMessage(MessageLevel.INFO, "File '" + fileName + "' contains uppercase letters. Lowercase recommended.");
        }
        if (id.contains(" ")) {
            addMessage(MessageLevel.INFO, "File '" + fileName + "' contains spaces. Use underscores instead.");
        }
        if (!id.matches("[a-zA-Z0-9_\\-]+")) {
            addMessage(MessageLevel.INFO, "File '" + fileName + "' contains special characters.");
        }
    }

    private static void removeEmptyStrings(List<String> values, String stageId, String label) {
        values.removeIf(value -> {
            boolean remove = value == null || value.isBlank();
            if (remove) {
                addMessage(MessageLevel.WARN, "Removed empty " + label + " entry from stage '" + stageId + "'.");
            }
            return remove;
        });
    }

    private static void removeEmptyItemEntries(List<ItemEntry> values, String stageId) {
        values.removeIf(value -> {
            boolean remove = value.getId() == null || value.getId().isBlank();
            if (remove) {
                addMessage(MessageLevel.WARN, "Removed empty item entry from stage '" + stageId + "'.");
            }
            return remove;
        });
    }

    private static void addMessage(MessageLevel level, String message) {
        LOADING_MESSAGES.add(new LoadingMessage(level, message));
        switch (level) {
            case ERROR -> DebugLogger.error("Stage Loading", message);
            case WARN -> DebugLogger.warn("Stage Loading", message);
            case INFO -> DebugLogger.info("Stage Loading", message);
        }
    }

    private static Path globalDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("historystages").resolve("global");
    }

    private static Path individualDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("historystages").resolve("individual");
    }

    private static Map<String, StageEntry> deserializeStages(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        java.lang.reflect.Type type = new TypeToken<Map<String, StageEntry>>() { }.getType();
        Map<String, StageEntry> decoded = GSON.fromJson(json, type);
        return decoded != null ? decoded : new HashMap<>();
    }
}
