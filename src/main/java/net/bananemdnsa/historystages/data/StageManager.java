package net.bananemdnsa.historystages.data;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.EntityLocks;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;

import net.bananemdnsa.historystages.data.dependency.*;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
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
import java.util.concurrent.ConcurrentHashMap;

public class StageManager {
    // ConcurrentHashMap (not HashMap) so the render thread can iterate
    // STAGES/INDIVIDUAL_STAGES safely while a stage save or network sync
    // mutates the map. HashMap.iterator() throws ConcurrentModificationException
    // in that race; CHM's iterators are weakly consistent and never throw.
    private static final Map<String, StageEntry> STAGES = new ConcurrentHashMap<>();
    private static final Map<String, StageEntry> INDIVIDUAL_STAGES = new ConcurrentHashMap<>();
    private static final List<LoadingMessage> LOADING_MESSAGES = new ArrayList<>();
    private static final Gson GSON = new Gson();

    // Dual-phase: entry ID → set of global stage IDs that share the entry with an individual stage
    private static final Map<String, Set<String>> DUAL_PHASE_ITEMS       = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_TAGS        = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_MODS        = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_DIMENSIONS  = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_STRUCTURES  = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_ATTACKLOCK  = new HashMap<>();
    // Reverse: entry ID → set of individual stage IDs (used for [Dual] badge on global stage entries)
    private static final Map<String, Set<String>> DUAL_PHASE_ITEMS_IND       = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_TAGS_IND        = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_MODS_IND        = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_DIMENSIONS_IND  = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_STRUCTURES_IND  = new HashMap<>();
    private static final Map<String, Set<String>> DUAL_PHASE_ATTACKLOCK_IND  = new HashMap<>();

    public enum MessageLevel { ERROR, WARN, INFO }
    public record LoadingMessage(MessageLevel level, String message) {}

    private static void addMessage(MessageLevel level, String message) {
        LOADING_MESSAGES.add(new LoadingMessage(level, message));
    }

    /** In MC 1.21+, ResourceLocation.isValidResourceLocation() was removed. Use tryParse() instead. */
    private static boolean isValidResourceLocation(String value) {
        return value != null && ResourceLocation.tryParse(value) != null;
    }

    private static final Set<String> KNOWN_KEYS = Set.of(
            "display_name", "research_time", "items", "tags", "mods",
            "mod_exceptions", "recipes", "dimensions", "structures", "entities", "dependencies", "icon",
            "min_pedestal_tier", "pedestal_tier_mode",
            "mode", "auto_trigger", "temporary", "hidden_display"
    );
    private static final Set<String> KNOWN_ENTITY_KEYS = Set.of(
            "spawnlock", "attacklock", "modLinked"
    );
    private static final Set<String> KNOWN_HIDDEN_DISPLAY_KEYS = Set.of(
            "name_mode", "name_text", "tooltip_mode", "tooltip_text", "show_lock_hints"
    );
    private static final Set<String> KNOWN_STRUCTURE_KEYS = Set.of(
            "structures", "mod_linked"
    );
    private static final Set<String> KNOWN_LOCK_ACTIONS = Set.of(
            "equip", "attack", "place", "break", "pickup", "use", "loot", "recipe", "gui", "icon"
    );
    private static final Set<String> KNOWN_SPAWN_SOURCES = Set.of(
            "natural", "spawner", "structure", "breeding", "summon", "spawn_egg"
    );

    public static void load() {
        STAGES.clear();
        INDIVIDUAL_STAGES.clear();
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

            validateFileName(id, file.getName());

            try (Reader reader = new FileReader(file)) {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                detectUnknownKeys(id, content);

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

        loadIndividual();

        net.bananemdnsa.historystages.data.auto.AutoTriggerManager.rebuildIndex();
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
            if (json.has("entities") && json.get("entities").isJsonObject()) {
                JsonObject entities = json.getAsJsonObject("entities");
                for (String key : entities.keySet()) {
                    if (!KNOWN_ENTITY_KEYS.contains(key)) {
                        addMessage(MessageLevel.WARN, "Unknown entity key '" + key + "' in stage '" + stageId + "'. Typo?");
                        DebugLogger.warn("Unknown Keys", "Unknown key 'entities." + key + "' in stage '" + stageId + "'. Known entity keys: " + KNOWN_ENTITY_KEYS + ".");
                    }
                }
            }
            if (json.has("structures") && json.get("structures").isJsonObject()) {
                JsonObject structures = json.getAsJsonObject("structures");
                for (String key : structures.keySet()) {
                    if (!KNOWN_STRUCTURE_KEYS.contains(key)) {
                        addMessage(MessageLevel.WARN, "Unknown structure key '" + key + "' in stage '" + stageId + "'. Typo?");
                        DebugLogger.warn("Unknown Keys", "Unknown key 'structures." + key + "' in stage '" + stageId + "'. Known structure keys: " + KNOWN_STRUCTURE_KEYS + ".");
                    }
                }
            }
            if (json.has("hidden_display") && json.get("hidden_display").isJsonObject()) {
                JsonObject hd = json.getAsJsonObject("hidden_display");
                for (String key : hd.keySet()) {
                    if (!KNOWN_HIDDEN_DISPLAY_KEYS.contains(key)) {
                        addMessage(MessageLevel.WARN, "Unknown hidden_display key '" + key + "' in stage '" + stageId + "'. Typo?");
                        DebugLogger.warn("Unknown Keys", "Unknown key 'hidden_display." + key + "' in stage '" + stageId + "'. Known hidden_display keys: " + KNOWN_HIDDEN_DISPLAY_KEYS + ".");
                    }
                }
                // Name supports only off/replace; tooltip additionally supports hidden.
                checkDisplayMode(stageId, hd, "name_mode", Set.of("off", "replace"));
                checkDisplayMode(stageId, hd, "tooltip_mode", Set.of("off", "hidden", "replace"));
            }
        } catch (Exception ignored) {}
    }

    private static void checkDisplayMode(String stageId, JsonObject hd, String key, Set<String> allowed) {
        if (!hd.has(key) || !hd.get(key).isJsonPrimitive()) return;
        String val = hd.get(key).getAsString().trim().toLowerCase(java.util.Locale.ROOT);
        if (!allowed.contains(val)) {
            addMessage(MessageLevel.WARN, "Invalid hidden_display." + key + " '" + val + "' in stage '" + stageId + "'.");
            DebugLogger.warn("Invalid Values", "Invalid value 'hidden_display." + key + "' = '" + val
                    + "' in stage '" + stageId + "'. Allowed: " + allowed + ". Defaults to 'off'.");
        }
    }

    private static void validateAndAdd(String stageId, StageEntry entry) {

        if (entry.getDisplayName().equals("Unknown Stage")) {
            addMessage(MessageLevel.WARN, "Stage '" + stageId + "' has no 'display_name'. Defaults to 'Unknown Stage'.");
            DebugLogger.warn("Missing Fields", "Stage '" + stageId + "' has no 'display_name' set. It will show as 'Unknown Stage'.");
        }

        removeEmptyItemEntries(entry.getItemEntries(), stageId);
        removeEmptyStrings(entry.getTags(), stageId, "tags");
        removeEmptyStrings(entry.getMods(), stageId, "mods");
        removeEmptyItemEntries(entry.getModExceptionEntries(), stageId);
        removeEmptyStrings(entry.getRecipes(), stageId, "recipes");
        removeEmptyStrings(entry.getDimensions(), stageId, "dimensions");
        removeEmptyStrings(entry.getStructures(), stageId, "structures");
        removeEmptyStrings(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");
        removeEmptySpawnlockEntries(entry.getEntities().getSpawnlock(), stageId);

        checkDuplicateItems(entry.getItemEntries(), stageId);
        checkDuplicates(entry.getTags(), stageId, "tags");
        checkDuplicates(entry.getMods(), stageId, "mods");
        checkDuplicateItems(entry.getModExceptionEntries(), stageId);
        checkDuplicates(entry.getRecipes(), stageId, "recipes");
        checkDuplicates(entry.getDimensions(), stageId, "dimensions");
        checkDuplicates(entry.getStructures(), stageId, "structures");
        checkDuplicates(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");
        checkDuplicateSpawnlock(entry.getEntities().getSpawnlock(), stageId);

        entry.getItemEntries().removeIf(item -> {
            String itemId = item.getId();
            if (!isValidResourceLocation(itemId)) {
                addMessage(MessageLevel.WARN, "Item '" + itemId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Items", "Item '" + itemId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getTags().removeIf(tagId -> {
            if (!isValidResourceLocation(tagId)) {
                addMessage(MessageLevel.WARN, "Tag '" + tagId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Tags", "Tag '" + tagId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

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

        Set<String> lockedMods = new HashSet<>(entry.getMods());
        entry.getModExceptionEntries().removeIf(exceptionEntry -> {
            String exItemId = exceptionEntry.getId();
            if (!isValidResourceLocation(exItemId)) {
                addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Mod Exceptions", "Mod exception '" + exItemId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            ResourceLocation rl = ResourceLocation.parse(exItemId);
            if (!lockedMods.contains(rl.getNamespace())) {
                addMessage(MessageLevel.ERROR, "Mod exception '" + exItemId + "' does not belong to a locked mod (Stage: " + stageId + "). Removed.");
                DebugLogger.error("Invalid Mod Exceptions", "Mod exception '" + exItemId + "' belongs to mod '" + rl.getNamespace() + "' which is not in the 'mods' list (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getDimensions().removeIf(dimId -> {
            if (!isValidResourceLocation(dimId)) {
                addMessage(MessageLevel.WARN, "Dimension '" + dimId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Dimensions", "Dimension '" + dimId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Structures (plain IDs and "#tag" entries allowed) ---
        entry.getStructures().removeIf(structId -> {
            String check = structId != null && structId.startsWith("#") ? structId.substring(1) : structId;
            if (!isValidResourceLocation(check)) {
                addMessage(MessageLevel.WARN, "Structure '" + structId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Structures", "Structure '" + structId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getRecipes().removeIf(recipeId -> {
            if (!isValidResourceLocation(recipeId)) {
                addMessage(MessageLevel.WARN, "Recipe '" + recipeId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Recipes", "Recipe '" + recipeId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getEntities().getAttacklock().removeIf(entityId -> {
            if (!isValidResourceLocation(entityId)) {
                addMessage(MessageLevel.WARN, "Entity attacklock '" + entityId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Entities", "Entity attacklock '" + entityId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getEntities().getSpawnlock().removeIf(spEntry -> {
            String entityId = spEntry.getId();
            if (!isValidResourceLocation(entityId)) {
                addMessage(MessageLevel.WARN, "Entity spawnlock '" + entityId + "' invalid format (Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Entities", "Entity spawnlock '" + entityId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // Validate per-entry unlock_sources lists
        for (EntitySpawnLockEntry spEntry : entry.getEntities().getSpawnlock()) {
            validateLockSources(spEntry.getLockSources(), stageId, spEntry.getId());
        }

        for (EntitySpawnLockEntry spEntry : entry.getEntities().getSpawnlock()) {
            String entityId = spEntry.getId();
            // Only "block all sources" entries imply attacklock — selective ones don't.
            if (!spEntry.hasLockSources() && entry.getEntities().getAttacklock().contains(entityId)) {
                addMessage(MessageLevel.INFO, "Entity '" + entityId + "' in both attacklock and spawnlock (Stage: " + stageId + "). Redundant.");
                DebugLogger.info("Redundant Entities", "Entity '" + entityId + "' is in both attacklock and spawnlock (Stage: " + stageId + "). Spawnlock already implies attacklock — the attacklock entry is redundant.");
            }
        }

        if (entry.getResearchTime() < 0) {
            addMessage(MessageLevel.INFO, "Stage '" + stageId + "' has negative research_time (" + entry.getResearchTime() + "). Using global default.");
            DebugLogger.info("Configuration", "Stage '" + stageId + "' has negative research_time (" + entry.getResearchTime() + "). Falling back to global default.");
        }

        // --- Icon validation ---
        String iconVal = entry.getIcon();
        if (iconVal != null && !iconVal.isEmpty()) {
            if (!isValidResourceLocation(iconVal)) {
                addMessage(MessageLevel.WARN, "Icon '" + iconVal + "' invalid format (Stage: " + stageId + "). Cleared.");
                DebugLogger.warn("Invalid Icon", "Icon '" + iconVal + "' is not a valid ResourceLocation (Stage: " + stageId + "). Cleared.");
                entry.setIcon(null);
            }
        }

        // --- Lock actions: validate per-entry unlock_actions lists ---
        for (ItemEntry item : entry.getItemEntries()) {
            validateLockActions(item.getLockActions(), stageId, item.getId(), "items");
        }
        for (NamedLockEntry tag : entry.getTagEntries()) {
            validateLockActions(tag.getLockActions(), stageId, tag.getId(), "tags");
        }
        for (NamedLockEntry mod : entry.getModEntries()) {
            validateLockActions(mod.getLockActions(), stageId, mod.getId(), "mods");
        }

        // --- Dependencies validation ---
        if (entry.hasDependencies()) {
            int groupIdx = 0;
            for (DependencyGroup group : entry.getDependencies()) {
                groupIdx++;
                String groupLabel = "Stage: " + stageId + ", group " + groupIdx;

                // Validate logic field
                String logic = group.getLogic();
                if (!"AND".equalsIgnoreCase(logic) && !"OR".equalsIgnoreCase(logic)) {
                    String msg = "Dependency group has invalid logic '" + logic + "' (" + groupLabel + "). Must be AND or OR. Defaulting to AND.";
                    addMessage(MessageLevel.WARN, msg);
                    DebugLogger.warn("Invalid Dependencies", msg);
                    group.setLogic("AND");
                }

                // Validate items
                group.getItems().removeIf(depItem -> {
                    if (depItem.getId() == null || !isValidResourceLocation(depItem.getId())) {
                        String msg = "Dependency item '" + depItem.getId() + "' invalid format (" + groupLabel + "). Removed.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        return true;
                    }
                    if (depItem.getCount() < 1) {
                        String msg = "Dependency item '" + depItem.getId() + "' has invalid count " + depItem.getCount() + " (" + groupLabel + "). Corrected to 1.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        depItem.setCount(1);
                    }
                    return false;
                });

                // Validate individual_stages
                group.getIndividualStages().removeIf(dep -> {
                    if (dep.getStageId() == null || dep.getStageId().isBlank()) {
                        String msg = "Dependency individual_stage entry has no stage_id (" + groupLabel + "). Removed.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        return true;
                    }
                    String mode = dep.getMode();
                    if (!"all_online".equals(mode) && !"all_ever".equals(mode)) {
                        String msg = "Dependency individual_stage '" + dep.getStageId() + "' has invalid mode '" + mode + "' (" + groupLabel + "). Defaulting to all_online.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        dep.setMode("all_online");
                    }
                    return false;
                });

                // Validate entity kills
                group.getEntityKills().removeIf(kill -> {
                    if (kill.getEntityId() == null || !isValidResourceLocation(kill.getEntityId())) {
                        String msg = "Dependency entity kill '" + kill.getEntityId() + "' invalid format (" + groupLabel + "). Removed.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        return true;
                    }
                    if (kill.getCount() < 1) {
                        String msg = "Dependency entity kill '" + kill.getEntityId() + "' has invalid count " + kill.getCount() + " (" + groupLabel + "). Corrected to 1.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        kill.setCount(1);
                    }
                    return false;
                });

                // Validate stats
                group.getStats().removeIf(stat -> {
                    if (stat.getStatId() == null || !isValidResourceLocation(stat.getStatId())) {
                        String msg = "Dependency stat '" + stat.getStatId() + "' invalid format (" + groupLabel + "). Removed.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        return true;
                    }
                    if (stat.getMinValue() < 0) {
                        String msg = "Dependency stat '" + stat.getStatId() + "' has invalid min_value " + stat.getMinValue() + " (" + groupLabel + "). Corrected to 0.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        stat.setMinValue(0);
                    }
                    return false;
                });

                // Validate advancements
                group.getAdvancements().removeIf(adv -> {
                    if (adv == null || !isValidResourceLocation(adv)) {
                        String msg = "Dependency advancement '" + adv + "' invalid format (" + groupLabel + "). Removed.";
                        addMessage(MessageLevel.WARN, msg);
                        DebugLogger.warn("Invalid Dependencies", msg);
                        return true;
                    }
                    return false;
                });

                // Validate xp_level
                if (group.getXpLevel() != null && group.getXpLevel().getLevel() < 0) {
                    String msg = "Dependency xp_level has negative level " + group.getXpLevel().getLevel() + " (" + groupLabel + "). Corrected to 0.";
                    addMessage(MessageLevel.WARN, msg);
                    DebugLogger.warn("Invalid Dependencies", msg);
                    group.getXpLevel().setLevel(0);
                }

                // Warn about unknown stage references (non-fatal, stage might not be loaded yet)
                for (String depStageId : group.getStages()) {
                    StageEntry depEntry = STAGES.get(depStageId);
                    if (depEntry == null) {
                        addMessage(MessageLevel.INFO, "Dependency stage '" + depStageId + "' not found (Stage: " + stageId + "). May load later.");
                        DebugLogger.info("Invalid Dependencies", "Dependency stage '" + depStageId + "' not found (" + groupLabel + "). May be loaded later or is an individual stage.");
                    } else if (depEntry.getMode() == StageMode.TEMPORARY) {
                        // Temporary stages re-lock on their own without cascading to dependents,
                        // so a dependency on one only reflects its state at the moment of the
                        // dependent's own unlock check. The editor hides temporary stages from the
                        // dependency picker — this only fires for hand-edited JSON.
                        addMessage(MessageLevel.WARN, "Dependency stage '" + depStageId + "' is mode=temporary (Stage: " + stageId + "). Not recommended.");
                        DebugLogger.warn("Temporary Dependency", "Stage '" + stageId + "' depends on temporary stage '" + depStageId + "' (" + groupLabel + "). When a temporary stage re-locks, dependents are NOT re-locked — the dependency only matters at the dependent's own unlock check. Depending on a temporary stage is not recommended.");
                    }
                }
            }
        }

        // --- Mode ---
        String rawMode = entry.getRawMode();
        if (rawMode != null && !StageMode.isKnown(rawMode)) {
            addMessage(MessageLevel.WARN, "Stage '" + stageId + "' has unknown mode '" + rawMode + "'. Defaulting to 'default'.");
            DebugLogger.warn("Invalid Mode", "Stage '" + stageId + "' has unknown mode '" + rawMode + "'. Allowed: default, auto, external. Defaulting to 'default'.");
        }

        StageMode resolvedMode = entry.getMode();
        AutoTrigger autoTrig = entry.getAutoTrigger();

        if (resolvedMode.usesAutoTrigger()) {
            String modeName = resolvedMode.serialize();
            if (autoTrig == null || autoTrig.isEmpty()) {
                addMessage(MessageLevel.WARN, "Stage '" + stageId + "' is mode=" + modeName + " but has no triggers. It will never auto-unlock.");
                DebugLogger.warn("Empty AutoTrigger", "Stage '" + stageId + "' has mode=" + modeName + " with empty or missing 'auto_trigger.triggers'. It will never auto-unlock — must be unlocked via command or dependency cascade.");
            } else {
                for (TriggerCondition t : autoTrig.getTriggers()) {
                    validateTriggerCondition(stageId, t);
                }
                String rawCombineMode = autoTrig.getRawMode();
                if (rawCombineMode != null
                        && !rawCombineMode.equalsIgnoreCase("any")
                        && !rawCombineMode.equalsIgnoreCase("all")) {
                    addMessage(MessageLevel.WARN, "Stage '" + stageId + "' auto_trigger.mode '" + rawCombineMode + "' is invalid. Defaulting to 'any'.");
                    DebugLogger.warn("Invalid AutoTrigger Mode", "Stage '" + stageId + "' has auto_trigger.mode '" + rawCombineMode + "'. Expected 'any' or 'all'. Defaulting to 'any'.");
                }
            }
        } else if (autoTrig != null && !autoTrig.isEmpty()) {
            addMessage(MessageLevel.INFO, "Stage '" + stageId + "' has auto_trigger but mode=" + resolvedMode.serialize() + ". The auto_trigger will be ignored.");
            DebugLogger.info("Unused AutoTrigger", "Stage '" + stageId + "' has an auto_trigger configured but its mode is '" + resolvedMode.serialize() + "'. The auto_trigger will be ignored (only mode=auto/temporary uses it).");
        }

        // --- Temporary-mode config ---
        var tempCfg = entry.getTemporary();
        if (resolvedMode == StageMode.TEMPORARY) {
            if (tempCfg == null) {
                addMessage(MessageLevel.WARN, "Stage '" + stageId + "' is mode=temporary but has no 'temporary' config. Using defaults (1 hour, not re-triggerable).");
                DebugLogger.warn("Missing Temporary Config", "Stage '" + stageId + "' has mode=temporary but no 'temporary' object. Defaulting to duration=1 hour, re_triggerable=false.");
                tempCfg = new net.bananemdnsa.historystages.data.temporary.TemporaryConfig();
                entry.setTemporary(tempCfg);
            }
            if (tempCfg.getDuration() <= 0) {
                addMessage(MessageLevel.WARN, "Stage '" + stageId + "' has temporary.duration <= 0. Corrected to 1.");
                DebugLogger.warn("Invalid Temporary Config", "Stage '" + stageId + "' has temporary.duration of " + tempCfg.getDuration() + ". A temporary stage must stay unlocked for at least one unit. Corrected to 1.");
                tempCfg.setDuration(1);
            }
            if (!net.bananemdnsa.historystages.data.temporary.DurationUnit.isKnown(tempCfg.getRawDurationUnit())) {
                addMessage(MessageLevel.WARN, "Stage '" + stageId + "' has unknown temporary.duration_unit '" + tempCfg.getRawDurationUnit() + "'. Defaulting to 'hours'.");
                DebugLogger.warn("Invalid Temporary Config", "Stage '" + stageId + "' has unknown temporary.duration_unit '" + tempCfg.getRawDurationUnit() + "'. Allowed: minutes, hours, days. Defaulting to 'hours'.");
            }
            if (tempCfg.allowsMultiple()
                    && !net.bananemdnsa.historystages.data.temporary.DurationUnit.isKnown(tempCfg.getRawCooldownUnit())) {
                addMessage(MessageLevel.WARN, "Stage '" + stageId + "' has unknown temporary.cooldown_unit '" + tempCfg.getRawCooldownUnit() + "'. Defaulting to 'hours'.");
                DebugLogger.warn("Invalid Temporary Config", "Stage '" + stageId + "' has unknown temporary.cooldown_unit '" + tempCfg.getRawCooldownUnit() + "'. Allowed: minutes, hours, days. Defaulting to 'hours'.");
            }
        } else if (tempCfg != null) {
            addMessage(MessageLevel.INFO, "Stage '" + stageId + "' has a 'temporary' config but mode=" + resolvedMode.serialize() + ". It will be ignored.");
            DebugLogger.info("Unused Temporary Config", "Stage '" + stageId + "' has a 'temporary' config but its mode is '" + resolvedMode.serialize() + "'. The config will be ignored (only mode=temporary uses it).");
        }

        int totalEntries = entry.getItemEntries().size() + entry.getTags().size() + entry.getMods().size()
                + entry.getModExceptionEntries().size() + entry.getRecipes().size() + entry.getDimensions().size()
                + entry.getStructures().size() + entry.getEntities().getAttacklock().size() + entry.getEntities().getSpawnlock().size();
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

    /**
     * Validates a single lock-actions list (internal representation — locked actions):
     * reports unknown actions and duplicates without modifying the list.
     */
    private static void validateLockActions(List<String> actions, String stageId, String entryId, String fieldPath) {
        if (actions == null || actions.isEmpty()) return;

        // Unknown actions
        for (String action : actions) {
            if (action == null || !KNOWN_LOCK_ACTIONS.contains(action)) {
                addMessage(MessageLevel.WARN, "Unknown unlock_action '" + action + "' on '" + entryId + "' in " + fieldPath + " (Stage: " + stageId + ").");
                DebugLogger.warn("Invalid Lock Actions",
                        "Unknown unlock_action '" + action + "' on '" + entryId + "' in " + fieldPath + " (Stage: " + stageId + "). Known actions: " + KNOWN_LOCK_ACTIONS + ".");
            }
        }

        // Duplicates
        Set<String> seen = new HashSet<>();
        for (String action : actions) {
            if (!seen.add(action)) {
                DebugLogger.info("Duplicates",
                        "Duplicate unlock_action '" + action + "' on '" + entryId + "' in " + fieldPath + " (Stage: " + stageId + ").");
            }
        }
    }

    private static void removeEmptySpawnlockEntries(List<EntitySpawnLockEntry> list, String stageId) {
        int removed = 0;
        var it = list.iterator();
        while (it.hasNext()) {
            EntitySpawnLockEntry entry = it.next();
            if (entry.getId() == null || entry.getId().isBlank()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            addMessage(MessageLevel.WARN, "Removed " + removed + " empty entry(s) from 'entities.spawnlock' (Stage: " + stageId + ").");
            DebugLogger.warn("Empty Entries", "Removed " + removed + " empty/blank entry(s) from 'entities.spawnlock' (Stage: " + stageId + ").");
        }
    }

    private static void checkDuplicateSpawnlock(List<EntitySpawnLockEntry> list, String stageId) {
        Set<String> seen = new HashSet<>();
        for (EntitySpawnLockEntry entry : list) {
            if (!seen.add(entry.getId())) {
                addMessage(MessageLevel.INFO, "Duplicate '" + entry.getId() + "' in 'entities.spawnlock' (Stage: " + stageId + ").");
                DebugLogger.info("Duplicates", "Duplicate entry '" + entry.getId() + "' in 'entities.spawnlock' (Stage: " + stageId + ").");
            }
        }
    }

    /** Validates per-entry unlock_sources lists (internal representation = locked sources). */
    private static void validateLockSources(List<String> sources, String stageId, String entryId) {
        if (sources == null || sources.isEmpty()) return;
        for (String src : sources) {
            if (src == null || !KNOWN_SPAWN_SOURCES.contains(src)) {
                addMessage(MessageLevel.WARN, "Unknown unlock_source '" + src + "' on '" + entryId + "' in entities.spawnlock (Stage: " + stageId + ").");
                DebugLogger.warn("Invalid Spawn Sources",
                        "Unknown unlock_source '" + src + "' on '" + entryId + "' in entities.spawnlock (Stage: " + stageId + "). Known sources: " + KNOWN_SPAWN_SOURCES + ".");
            }
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

    // Keep backwards compat for code that uses getLoadingErrors()
    public static List<String> getLoadingErrors() {
        List<String> errors = new ArrayList<>();
        for (LoadingMessage msg : LOADING_MESSAGES) {
            String prefix = switch (msg.level()) {
                case ERROR -> "§c[" + msg.level() + "] ";
                case WARN -> "§e[" + msg.level() + "] ";
                case INFO -> "§7[" + msg.level() + "] ";
            };
            errors.add(prefix + "§f" + msg.message());
        }
        return errors;
    }

    public static void reloadStages() {
        load();
        // After re-indexing AUTO stages, drop progress entries that no longer
        // correspond to indexed AUTO stages (e.g. mode flipped AUTO → DEFAULT
        // via the editor, or auto_trigger was removed).
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.overworld() != null) {
            AutoTriggerManager.pruneOrphans(server.overworld());
            // Drop temporary-timer state for stages that no longer exist or are no
            // longer mode=temporary (e.g. mode flipped via the editor).
            net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(server.overworld())
                    .pruneOrphans(temporaryStageIds());
        }
    }

    /** Ids of all loaded stages (global + individual) currently in mode=temporary. */
    public static Set<String> temporaryStageIds() {
        Set<String> ids = new HashSet<>();
        for (var e : STAGES.entrySet()) {
            if (e.getValue().getMode() == StageMode.TEMPORARY) ids.add(e.getKey());
        }
        for (var e : INDIVIDUAL_STAGES.entrySet()) {
            if (e.getValue().getMode() == StageMode.TEMPORARY) ids.add(e.getKey());
        }
        return ids;
    }

    /**
     * Validates stage entries against the actual registries.
     * Must be called AFTER registries are fully loaded (e.g. on world load).
     */
    public static void validateAgainstRegistries() {
        for (Map.Entry<String, StageEntry> stageEntry : STAGES.entrySet()) {
            String stageId = stageEntry.getKey();
            StageEntry entry = stageEntry.getValue();

            for (String itemId : entry.getAllItemIds()) {
                if (!isValidResourceLocation(itemId)) continue;
                ResourceLocation rl = ResourceLocation.parse(itemId);
                if (!BuiltInRegistries.ITEM.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Item '" + itemId + "' does not exist in registry (Stage: " + stageId + ").");
                    DebugLogger.warn("Unknown Items", "Item '" + itemId + "' is a valid ResourceLocation but does not exist in the item registry (Stage: " + stageId + "). Typo or missing mod?");
                }
            }

            for (String exItemId : entry.getAllModExceptionIds()) {
                if (!isValidResourceLocation(exItemId)) continue;
                ResourceLocation rl = ResourceLocation.parse(exItemId);
                if (!BuiltInRegistries.ITEM.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' does not exist in registry (Stage: " + stageId + ").");
                    DebugLogger.warn("Unknown Mod Exceptions", "Mod exception '" + exItemId + "' does not exist in the item registry (Stage: " + stageId + "). Typo or missing mod?");
                }
            }

            for (String entityId : entry.getEntities().getAttacklock()) {
                if (!isValidResourceLocation(entityId)) continue;
                ResourceLocation rl = ResourceLocation.parse(entityId);
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Entity '" + entityId + "' does not exist in registry (Stage: " + stageId + ", attacklock).");
                    DebugLogger.warn("Unknown Entities", "Entity '" + entityId + "' does not exist in the entity registry (Stage: " + stageId + ", attacklock). Typo or missing mod?");
                }
            }
            for (EntitySpawnLockEntry spEntry : entry.getEntities().getSpawnlock()) {
                String entityId = spEntry.getId();
                if (!isValidResourceLocation(entityId)) continue;
                ResourceLocation rl = ResourceLocation.parse(entityId);
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
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
                if (!isValidResourceLocation(itemId)) continue;
                ResourceLocation rl = ResourceLocation.parse(itemId);
                if (!BuiltInRegistries.ITEM.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Item '" + itemId + "' does not exist in registry (Individual Stage: " + indId + ").");
                    DebugLogger.warn("Unknown Items", "Item '" + itemId + "' does not exist in the item registry (Individual Stage: " + indId + "). Typo or missing mod?");
                }
            }

            for (String exItemId : indData.getAllModExceptionIds()) {
                if (!isValidResourceLocation(exItemId)) continue;
                ResourceLocation rl = ResourceLocation.parse(exItemId);
                if (!BuiltInRegistries.ITEM.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' does not exist in registry (Individual Stage: " + indId + ").");
                    DebugLogger.warn("Unknown Mod Exceptions", "Mod exception '" + exItemId + "' does not exist in the item registry (Individual Stage: " + indId + "). Typo or missing mod?");
                }
            }

            for (String entityId : indData.getEntities().getAttacklock()) {
                if (!isValidResourceLocation(entityId)) continue;
                ResourceLocation rl = ResourceLocation.parse(entityId);
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                    addMessage(MessageLevel.WARN, "Entity '" + entityId + "' does not exist in registry (Individual Stage: " + indId + ", attacklock).");
                    DebugLogger.warn("Unknown Entities", "Entity '" + entityId + "' does not exist in the entity registry (Individual Stage: " + indId + ", attacklock). Typo or missing mod?");
                }
            }
        }
    }

    public static Map<String, StageEntry> getStages() {
        return STAGES;
    }

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

            List<NamedLockEntry> tags = data.getTagEntries();
            if (!tags.isEmpty()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                if (item != null) {
                    for (NamedLockEntry tagEntry : tags) {
                        // NBT tags need a stack to evaluate; this stackless path skips them.
                        if (!tagEntry.hasNbt() && item.builtInRegistryHolder().is(tagEntry.getItemTagKey())) return stageName;
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
            if (locks.getAttacklock().contains(entityId)) {
                allFoundStages.add(entry.getKey());
                continue;
            }
            // A spawnlock entry implies attacklock only when it blocks ALL sources.
            for (EntitySpawnLockEntry spEntry : locks.getSpawnlock()) {
                if (spEntry.getId().equals(entityId) && !spEntry.hasLockSources()) {
                    allFoundStages.add(entry.getKey());
                    break;
                }
            }
        }
        return allFoundStages;
    }

    /**
     * Returns the stages that block the given entity for the given spawn source.
     * A stage blocks the source if its spawnlock contains an entry for the entity that
     * either has no source filter (= block all) or explicitly lists this source.
     */
    public static List<String> getAllStagesForSpawnLockedEntity(String entityId, String source, String dimension) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            for (EntitySpawnLockEntry spEntry : entry.getValue().getEntities().getSpawnlock()) {
                if (spEntry.getId().equals(entityId)
                        && spEntry.blocksSource(source)
                        && spEntry.blocksDimension(dimension)) {
                    allFoundStages.add(entry.getKey());
                    break;
                }
            }
        }
        return allFoundStages;
    }

    /** Returns stages that have an entry for this entity blocking the given dimension (any source). Used by EntityJoinLevel fallback. */
    public static List<String> getAllStagesWithSpawnlockEntry(String entityId, String dimension) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            for (EntitySpawnLockEntry spEntry : entry.getValue().getEntities().getSpawnlock()) {
                if (spEntry.getId().equals(entityId) && spEntry.blocksDimension(dimension)) {
                    allFoundStages.add(entry.getKey());
                    break;
                }
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

    public static List<String> getAllStagesForItemOrMod(String itemId, String modId) {
        return getAllStagesForItemOrMod(itemId, modId, null);
    }

    public static List<String> getAllStagesForItemOrMod(String itemId, String modId, ItemStack stack) {
        List<String> allFoundStages = new ArrayList<>();
        Item item = stack != null ? stack.getItem() : BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));

        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            String stageName = entry.getKey();
            StageEntry data = entry.getValue();

            boolean match = false;
            // Check Item ID (with NBT matching)
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
            // Check Tags
            if (!match && item != null) {
                for (NamedLockEntry tagEntry : data.getTagEntries()) {
                    if (tagEntryMatches(stack, item, tagEntry)) {
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

    private static boolean isModException(String itemId, ItemStack stack, StageEntry data) {
        return data.isModExcepted(itemId, stack);
    }

    /**
     * Returns true when {@code item} is in the tag entry's tag AND, if the entry carries an
     * NBT criterion, the stack matches it. When the entry has NBT but no stack is available,
     * this returns false (cannot confirm) — mirroring how NBT item entries behave stacklessly.
     */
    public static boolean tagEntryMatches(ItemStack stack, Item item, NamedLockEntry tagEntry) {
        if (item == null) return false;
        if (!item.builtInRegistryHolder().is(tagEntry.getItemTagKey())) return false;
        if (!tagEntry.hasNbt()) return true;
        return stack != null && NbtMatcher.matches(stack, tagEntry.getNbt());
    }

    /**
     * Checks whether a specific lock action applies to an item in the given stage entry.
     * Returns true when the item matches this stage AND the action is restricted
     * (either because no unlock_actions field is set — all actions locked — or because
     * the action is NOT in the unlock_actions list).
     * Returns false when the item does not match this stage at all.
     */
    public static boolean isItemActionLockedForStage(String itemId, String modId,
            net.minecraft.world.item.ItemStack stack, String action, StageEntry data) {
        Item item = stack != null ? stack.getItem() : null;

        for (ItemEntry entry : data.getItemEntries()) {
            if (!entry.getId().equals(itemId)) continue;
            boolean nbtMatch = !entry.hasNbt() || (stack != null && NbtMatcher.matches(stack, entry.getNbt()));
            if (nbtMatch) {
                return isActionInList(entry.getLockActions(), action);
            }
        }

        for (NamedLockEntry modEntry : data.getModEntries()) {
            if (modEntry.getId().equals(modId) && !isModException(itemId, stack, data)) {
                return isActionInList(modEntry.getLockActions(), action);
            }
        }

        if (item != null) {
            for (NamedLockEntry tagEntry : data.getTagEntries()) {
                if (tagEntryMatches(stack, item, tagEntry)) {
                    return isActionInList(tagEntry.getLockActions(), action);
                }
            }
        }

        return false;
    }

    /**
     * Returns true when the action should be blocked:
     * null = all actions locked (default, no unlock_actions field in JSON).
     * empty list = no actions locked (all unlocked).
     * non-empty list = only the listed actions are locked.
     */
    private static boolean isActionInList(List<String> lockActions, String action) {
        if (lockActions == null) return true;
        return lockActions.contains(action);
    }

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

    public static boolean isRecipeIdLocked(String recipeId, boolean isClientSide) {
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            if (entry.getValue().getRecipes().contains(recipeId)) {
                if (isClientSide) {
                    if (!net.bananemdnsa.historystages.client.cache.ClientStageCache.isStageUnlocked(entry.getKey())) {
                        return true;
                    }
                } else {
                    if (!net.bananemdnsa.historystages.data.saveddata.StageData.SERVER_CACHE.contains(entry.getKey())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isItemLockedForServer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return false;

        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace(), stack);
        if (requiredStages.isEmpty()) return false;

        for (String stage : requiredStages) {
            if (!net.bananemdnsa.historystages.data.saveddata.StageData.SERVER_CACHE.contains(stage)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isItemLocked(ItemStack stack, boolean isClientSide) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return false;

        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace(), stack);
        if (requiredStages.isEmpty()) return false;

        for (String stage : requiredStages) {
            if (isClientSide) {
                if (!net.bananemdnsa.historystages.client.cache.ClientStageCache.isStageUnlocked(stage)) return true;
            } else {
                if (!net.bananemdnsa.historystages.data.saveddata.StageData.SERVER_CACHE.contains(stage)) return true;
            }
        }
        return false;
    }

    // =============================================
    // INDIVIDUAL STAGES
    // =============================================

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

        detectOverlaps();

        System.out.println("[HistoryStages] Individual Stages geladen: " + INDIVIDUAL_STAGES.size());

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

    private static void validateAndAddIndividual(String stageId, StageEntry entry) {
        if (STAGES.containsKey(stageId)) {
            String msg = "Individual stage '" + stageId + "' has the same ID as a global stage. Individual stage skipped.";
            addMessage(MessageLevel.ERROR, msg);
            DebugLogger.error("Individual Stage Loading", msg);
            return;
        }

        removeEmptyItemEntries(entry.getItemEntries(), stageId);
        removeEmptyStrings(entry.getTags(), stageId, "tags");
        removeEmptyStrings(entry.getMods(), stageId, "mods");
        removeEmptyItemEntries(entry.getModExceptionEntries(), stageId);
        removeEmptyStrings(entry.getDimensions(), stageId, "dimensions");
        removeEmptyStrings(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");

        checkDuplicateItems(entry.getItemEntries(), stageId);
        checkDuplicates(entry.getTags(), stageId, "tags");
        checkDuplicates(entry.getMods(), stageId, "mods");
        checkDuplicateItems(entry.getModExceptionEntries(), stageId);
        checkDuplicates(entry.getDimensions(), stageId, "dimensions");
        checkDuplicates(entry.getEntities().getAttacklock(), stageId, "entities.attacklock");

        entry.getItemEntries().removeIf(item -> {
            if (!isValidResourceLocation(item.getId())) {
                addMessage(MessageLevel.WARN, "Item '" + item.getId() + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getTags().removeIf(tagId -> {
            if (!isValidResourceLocation(tagId)) {
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

        Set<String> indLockedMods = new HashSet<>(entry.getMods());
        entry.getModExceptionEntries().removeIf(exceptionEntry -> {
            String exItemId = exceptionEntry.getId();
            if (!isValidResourceLocation(exItemId)) {
                addMessage(MessageLevel.WARN, "Mod exception '" + exItemId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            ResourceLocation rl = ResourceLocation.parse(exItemId);
            if (!indLockedMods.contains(rl.getNamespace())) {
                addMessage(MessageLevel.ERROR, "Mod exception '" + exItemId + "' does not belong to a locked mod (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getDimensions().removeIf(dimId -> {
            if (!isValidResourceLocation(dimId)) {
                addMessage(MessageLevel.WARN, "Dimension '" + dimId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        removeEmptyStrings(entry.getStructures(), stageId, "structures");
        checkDuplicates(entry.getStructures(), stageId, "structures");
        entry.getStructures().removeIf(structId -> {
            String check = structId != null && structId.startsWith("#") ? structId.substring(1) : structId;
            if (!isValidResourceLocation(check)) {
                addMessage(MessageLevel.WARN, "Structure '" + structId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                DebugLogger.warn("Invalid Structures", "Structure '" + structId + "' is not a valid ResourceLocation (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        entry.getEntities().getAttacklock().removeIf(entityId -> {
            if (!isValidResourceLocation(entityId)) {
                addMessage(MessageLevel.WARN, "Entity attacklock '" + entityId + "' invalid format (Individual Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // --- Lock actions: validate per-entry unlock_actions lists ---
        for (ItemEntry item : entry.getItemEntries()) {
            validateLockActions(item.getLockActions(), stageId, item.getId(), "items");
        }
        for (NamedLockEntry tag : entry.getTagEntries()) {
            validateLockActions(tag.getLockActions(), stageId, tag.getId(), "tags");
        }
        for (NamedLockEntry mod : entry.getModEntries()) {
            validateLockActions(mod.getLockActions(), stageId, mod.getId(), "mods");
        }

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
     * Overlapping entries are registered as dual-phase: first locked globally
     * (all paired global stages must be unlocked), then locked per-player.
     * Covers items, tags, mods, dimensions, structures, and entity attacklock.
     */
    private static void detectOverlaps() {
        // Build a lookup: entry ID -> set of all global stage IDs containing it
        Map<String, Set<String>> globalItemMap       = new HashMap<>();
        Map<String, Set<String>> globalTagMap        = new HashMap<>();
        Map<String, Set<String>> globalModMap        = new HashMap<>();
        Map<String, Set<String>> globalDimensionMap  = new HashMap<>();
        Map<String, Set<String>> globalStructureMap  = new HashMap<>();
        Map<String, Set<String>> globalAttacklockMap = new HashMap<>();

        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            String gStageId = entry.getKey();
            StageEntry gEntry = entry.getValue();
            for (String item : gEntry.getAllItemIds())
                globalItemMap.computeIfAbsent(item, k -> new HashSet<>()).add(gStageId);
            for (String tag : gEntry.getNbtFreeTags())
                globalTagMap.computeIfAbsent(tag, k -> new HashSet<>()).add(gStageId);
            for (String mod : gEntry.getMods())
                globalModMap.computeIfAbsent(mod, k -> new HashSet<>()).add(gStageId);
            for (String dim : gEntry.getDimensions())
                globalDimensionMap.computeIfAbsent(dim, k -> new HashSet<>()).add(gStageId);
            for (String struct : gEntry.getStructures())
                globalStructureMap.computeIfAbsent(struct, k -> new HashSet<>()).add(gStageId);
            for (String entityId : gEntry.getEntities().getAttacklock())
                globalAttacklockMap.computeIfAbsent(entityId, k -> new HashSet<>()).add(gStageId);
            // Spawnlocked entities are also attacklocked globally — but only when the entry blocks all sources.
            for (EntitySpawnLockEntry spEntry : gEntry.getEntities().getSpawnlock())
                if (!spEntry.hasLockSources())
                    globalAttacklockMap.computeIfAbsent(spEntry.getId(), k -> new HashSet<>()).add(gStageId);
        }

        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            String iStageId = entry.getKey();
            StageEntry iEntry = entry.getValue();

            for (ItemEntry itemEntry : iEntry.getItemEntries()) {
                registerDualPhase(DUAL_PHASE_ITEMS, DUAL_PHASE_ITEMS_IND, globalItemMap, itemEntry.getId(), "item", iStageId);
            }
            for (String tag : iEntry.getNbtFreeTags()) {
                registerDualPhase(DUAL_PHASE_TAGS, DUAL_PHASE_TAGS_IND, globalTagMap, tag, "tag", iStageId);
            }
            for (String mod : iEntry.getMods()) {
                registerDualPhase(DUAL_PHASE_MODS, DUAL_PHASE_MODS_IND, globalModMap, mod, "mod", iStageId);
            }
            for (String dim : iEntry.getDimensions()) {
                registerDualPhase(DUAL_PHASE_DIMENSIONS, DUAL_PHASE_DIMENSIONS_IND, globalDimensionMap, dim, "dimension", iStageId);
            }
            for (String struct : iEntry.getStructures()) {
                registerDualPhase(DUAL_PHASE_STRUCTURES, DUAL_PHASE_STRUCTURES_IND, globalStructureMap, struct, "structure", iStageId);
            }
            for (String entityId : iEntry.getEntities().getAttacklock()) {
                registerDualPhase(DUAL_PHASE_ATTACKLOCK, DUAL_PHASE_ATTACKLOCK_IND, globalAttacklockMap, entityId, "attacklock entity", iStageId);
            }
        }
    }

    /** Public entry-point for rebuilding dual-phase maps after stage definitions are updated (e.g. client sync). */
    public static void rebuildDualPhase() {
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
        detectOverlaps();
    }

    private static void registerDualPhase(Map<String, Set<String>> globalTarget, Map<String, Set<String>> indTarget,
                                          Map<String, Set<String>> globalMap,
                                          String entryId, String label, String iStageId) {
        Set<String> globalStages = globalMap.get(entryId);
        if (globalStages == null) return;
        globalTarget.computeIfAbsent(entryId, k -> new HashSet<>()).addAll(globalStages);
        indTarget.computeIfAbsent(entryId, k -> new HashSet<>()).add(iStageId);
        String msg = "Individual stage '" + iStageId + "' " + label + " '" + entryId
                + "' also in global stage(s) " + globalStages + " — dual-phase lock registered.";
        addMessage(MessageLevel.INFO, msg);
        DebugLogger.info("Dual-Phase Detection", msg);
    }

    public static Map<String, Set<String>> getDualPhaseItems()         { return DUAL_PHASE_ITEMS; }
    public static Map<String, Set<String>> getDualPhaseTags()          { return DUAL_PHASE_TAGS; }
    public static Map<String, Set<String>> getDualPhaseMods()          { return DUAL_PHASE_MODS; }
    public static Map<String, Set<String>> getDualPhaseDimensions()    { return DUAL_PHASE_DIMENSIONS; }
    public static Map<String, Set<String>> getDualPhaseStructures()    { return DUAL_PHASE_STRUCTURES; }
    public static Map<String, Set<String>> getDualPhaseAttacklock()    { return DUAL_PHASE_ATTACKLOCK; }
    public static Map<String, Set<String>> getDualPhaseItemsInd()      { return DUAL_PHASE_ITEMS_IND; }
    public static Map<String, Set<String>> getDualPhaseTagsInd()       { return DUAL_PHASE_TAGS_IND; }
    public static Map<String, Set<String>> getDualPhaseModsInd()       { return DUAL_PHASE_MODS_IND; }
    public static Map<String, Set<String>> getDualPhaseDimensionsInd() { return DUAL_PHASE_DIMENSIONS_IND; }
    public static Map<String, Set<String>> getDualPhaseStructuresInd() { return DUAL_PHASE_STRUCTURES_IND; }
    public static Map<String, Set<String>> getDualPhaseAttacklockInd() { return DUAL_PHASE_ATTACKLOCK_IND; }

    public static Map<String, StageEntry> getIndividualStages() {
        return INDIVIDUAL_STAGES;
    }

    public static void setIndividualStages(Map<String, StageEntry> stages) {
        INDIVIDUAL_STAGES.clear();
        if (stages != null) {
            INDIVIDUAL_STAGES.putAll(stages);
        }
    }

    public static List<String> getAllIndividualStagesForItemOrMod(String itemId, String modId) {
        return getAllIndividualStagesForItemOrMod(itemId, modId, null);
    }

    public static List<String> getAllIndividualStagesForItemOrMod(String itemId, String modId, ItemStack stack) {
        List<String> allFoundStages = new ArrayList<>();
        Item item = stack != null ? stack.getItem() : BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));

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
            if (!match && data.getMods().contains(modId)) {
                if (!isModException(itemId, stack, data)) {
                    match = true;
                }
            }
            if (!match && item != null) {
                for (NamedLockEntry tagEntry : data.getTagEntries()) {
                    if (tagEntryMatches(stack, item, tagEntry)) {
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

    public static int getIndividualResearchTimeInTicks(String stageId) {
        StageEntry entry = INDIVIDUAL_STAGES.get(stageId);
        if (entry != null && entry.getResearchTime() > 0) {
            return entry.getResearchTime() * 20;
        }
        return net.bananemdnsa.historystages.Config.COMMON.researchTimeInSeconds.get() * 20;
    }

    public static List<String> getAllStagesForStructure(String structureId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            List<String> structs = entry.getValue().getStructures();
            if (structs != null && structs.contains(structureId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static boolean anyStageHasStructures() {
        for (StageEntry entry : STAGES.values()) {
            if (entry.getStructures() != null && !entry.getStructures().isEmpty()) return true;
        }
        return false;
    }

    public static List<String> getAllIndividualStagesForStructure(String structureId) {
        List<String> allFoundStages = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : INDIVIDUAL_STAGES.entrySet()) {
            List<String> structs = entry.getValue().getStructures();
            if (structs != null && structs.contains(structureId)) {
                allFoundStages.add(entry.getKey());
            }
        }
        return allFoundStages;
    }

    public static boolean isIndividualStage(String stageId) {
        return INDIVIDUAL_STAGES.containsKey(stageId);
    }

    private static void validateTriggerCondition(String stageId, TriggerCondition t) {
        if (t == null) {
            addMessage(MessageLevel.WARN, "Stage '" + stageId + "' has a null auto_trigger entry (skipped during load).");
            return;
        }
        String typeName = t.type();
        switch (t) {
            case BiomeTrigger bt -> checkTriggerRl(stageId, typeName, bt.id());
            case StructureTrigger st -> checkTriggerRl(stageId, typeName, st.id());
            case DimensionTrigger dt -> checkTriggerRl(stageId, typeName, dt.id());
            case ItemTrigger it -> checkTriggerRl(stageId, typeName, it.id());
            case EntityTrigger et -> {
                checkTriggerRl(stageId, typeName, et.id());
                String sm = et.subMode();
                if (sm != null
                        && !sm.equalsIgnoreCase("any")
                        && !sm.equalsIgnoreCase("kill")
                        && !sm.equalsIgnoreCase("interact")) {
                    addMessage(MessageLevel.WARN, "Entity trigger '" + et.id() + "' in stage '" + stageId + "' has invalid sub_mode '" + sm + "'. Defaulting to 'any'.");
                    DebugLogger.warn("Invalid AutoTrigger SubMode", "Entity trigger '" + et.id() + "' in stage '" + stageId + "' has sub_mode '" + sm + "'. Expected 'any', 'kill', or 'interact'. Defaulting to 'any'.");
                }
            }
            case BlockPlaceTrigger bp -> checkTriggerRl(stageId, typeName, bp.id());
            case BlockBreakTrigger bb -> checkTriggerRl(stageId, typeName, bb.id());
            case AdvancementTrigger at -> checkTriggerRl(stageId, typeName, at.id());
            case PlaytimeTrigger pt -> {
                if (pt.days() < 0) {
                    addMessage(MessageLevel.WARN, "Playtime trigger in stage '" + stageId + "' has negative days (" + pt.days() + "). Treated as 0.");
                    DebugLogger.warn("Invalid AutoTrigger Days", "Playtime trigger in stage '" + stageId + "' has days=" + pt.days() + ". Negative values are clamped to 0 at runtime.");
                }
            }
        }
    }

    private static void checkTriggerRl(String stageId, String triggerType, String id) {
        if (!isValidResourceLocation(id)) {
            addMessage(MessageLevel.WARN, "Trigger '" + triggerType + "' in stage '" + stageId + "' has invalid id '" + id + "'. It will never match.");
            DebugLogger.warn("Invalid AutoTrigger Id", "Trigger '" + triggerType + "' in stage '" + stageId + "' has id '" + id + "' which is not a valid ResourceLocation. The trigger will never match — fix the id.");
        }
    }
}
