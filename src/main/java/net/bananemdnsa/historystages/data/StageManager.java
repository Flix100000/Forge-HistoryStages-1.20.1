package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
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
import java.util.List;
import java.util.Map;

public class StageManager {
    private static final Map<String, StageEntry> STAGES = new HashMap<>();
    private static final List<String> LOADING_ERRORS = new ArrayList<>(); // NEU
    private static final Gson GSON = new Gson();


    public static void load() {
        STAGES.clear();
        LOADING_ERRORS.clear();
        DebugLogger.clear();

        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").toFile();
        if (!configDir.exists()) configDir.mkdirs();

        File[] files = configDir.listFiles((dir, name) ->
                name.endsWith(".json") && !name.startsWith("_")
        );

        if (files == null) return;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                StageEntry entry = GSON.fromJson(reader, StageEntry.class);
                String id = file.getName().replace(".json", "");

                if (entry != null) {
                    validateAndAdd(id, entry);
                }
            } catch (Exception e) {
                String msg = "Error in file: " + file.getName() + " (Invalid JSON syntax, stage skipped)";
                LOADING_ERRORS.add("§c[Debug] " + msg);
                DebugLogger.error("Stage Loading", msg + " — " + e.getMessage());
            }
        }

        DebugLogger.setStagesLoaded(STAGES.size());
        DebugLogger.writeLogFile();
    }

    private static void validateAndAdd(String stageId, StageEntry entry) {
        // Items prüfen — nur Format-Validierung, NICHT gegen Registry prüfen!
        // Grund: load() wird im Mod-Konstruktor aufgerufen, zu diesem Zeitpunkt
        // sind die Registries anderer Mods (z.B. Create) noch nicht befüllt.
        // Eine Registry-Prüfung würde gültige Items fälschlicherweise entfernen.
        entry.getItems().removeIf(itemId -> {
            if (!ResourceLocation.isValidResourceLocation(itemId)) {
                LOADING_ERRORS.add("§7[Debug] §fItem §e" + itemId + " §finvalid format (Stage: §b" + stageId + "§f). Skipping.");
                DebugLogger.warn("Invalid Items", "Item '" + itemId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // Tags prüfen
        entry.getTags().removeIf(tagId -> {
            if (!ResourceLocation.isValidResourceLocation(tagId)) {
                LOADING_ERRORS.add("§7[Debug] §fTag §e" + tagId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                DebugLogger.warn("Invalid Tags", "Tag '" + tagId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // Mods prüfen — nur Format, nicht entfernen wenn Mod fehlt.
        // Fehlende Mods sind kein Fehler (optionale Mod-Abhängigkeiten sind normal).
        // Die Lock-Logik ignoriert nicht-geladene Mods ohnehin automatisch.
        entry.getMods().removeIf(modId -> {
            if (modId == null || modId.isEmpty() || modId.contains(" ")) {
                LOADING_ERRORS.add("§7[Debug] §fMod §e" + modId + " §finvalid format (Stage: §b" + stageId + "§f). Skipping.");
                DebugLogger.warn("Invalid Mods", "Mod ID '" + modId + "' has invalid format (Stage: " + stageId + "). Removed.");
                return true;
            }
            if (!ModList.get().isLoaded(modId)) {
                DebugLogger.info("Missing Mods", "Mod '" + modId + "' is not installed (Stage: " + stageId + "). Entry kept — will apply if mod is added later.");
            }
            return false;
        });

        // Dimensionen prüfen
        entry.getDimensions().removeIf(dimId -> {
            if (!ResourceLocation.isValidResourceLocation(dimId)) {
                LOADING_ERRORS.add("§7[Debug] §fDimension §e" + dimId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                DebugLogger.warn("Invalid Dimensions", "Dimension '" + dimId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // Recipes prüfen
        entry.getRecipes().removeIf(recipeId -> {
            if (!ResourceLocation.isValidResourceLocation(recipeId)) {
                LOADING_ERRORS.add("§7[Debug] §fRecipe §e" + recipeId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                DebugLogger.warn("Invalid Recipes", "Recipe '" + recipeId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // Entities prüfen (attacklock)
        entry.getEntities().getAttacklock().removeIf(entityId -> {
            if (!ResourceLocation.isValidResourceLocation(entityId)) {
                LOADING_ERRORS.add("§7[Debug] §fEntity (attacklock) §e" + entityId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                DebugLogger.warn("Invalid Entities", "Entity attacklock '" + entityId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // Entities prüfen (spawnlock)
        entry.getEntities().getSpawnlock().removeIf(entityId -> {
            if (!ResourceLocation.isValidResourceLocation(entityId)) {
                LOADING_ERRORS.add("§7[Debug] §fEntity (spawnlock) §e" + entityId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                DebugLogger.warn("Invalid Entities", "Entity spawnlock '" + entityId + "' is not a valid ResourceLocation (Stage: " + stageId + "). Removed.");
                return true;
            }
            return false;
        });

        // Research Time Info (nur negativ ist ein Fehler, 0 = globaler Default ist gewollt)
        if (entry.getResearchTime() < 0) {
            LOADING_ERRORS.add("§7[Debug] §fStage §b" + stageId + " §fhas negative 'research_time' (" + entry.getResearchTime() + "). Using global config default.");
            DebugLogger.warn("Configuration", "Stage '" + stageId + "' has negative research_time (" + entry.getResearchTime() + "). Falling back to global default.");
        }

        STAGES.put(stageId, entry);
        System.out.println("[HistoryStages] Stage geladen: " + stageId);
    }

    public static List<String> getLoadingErrors() {
        return LOADING_ERRORS;
    }

    public static void reloadStages() {
        load();
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
            if (data.getMods() != null && data.getMods().contains(modId)) return stageName;

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

    public static List<String> getAllStagesForItemOrMod(String itemId, String modId) {
        List<String> allFoundStages = new ArrayList<>();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));

        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            String stageName = entry.getKey();
            StageEntry data = entry.getValue();

            boolean match = false;
            // Check Item ID
            if (data.getItems().contains(itemId)) match = true;
            // Check Mod ID
            if (!match && data.getMods().contains(modId)) match = true;
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
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").toFile();
        if (!configDir.exists()) configDir.mkdirs();

        File file = new File(configDir, stageId + ".json");
        try (Writer writer = new FileWriter(file)) {
            writer.write(entry.toJson());
            STAGES.put(stageId, entry);
            return true;
        } catch (Exception e) {
            System.err.println("[HistoryStages] Failed to save stage: " + stageId + " - " + e.getMessage());
            DebugLogger.error("Stage Saving", "Failed to save stage '" + stageId + "': " + e.getMessage());
            DebugLogger.writeLogFile();
            return false;
        }
    }

    public static boolean deleteStage(String stageId) {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").toFile();
        File file = new File(configDir, stageId + ".json");
        if (file.exists() && file.delete()) {
            STAGES.remove(stageId);
            return true;
        }
        return false;
    }

    public static List<String> getStageOrder() {
        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").toFile();
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
        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace());

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

        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace());
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


}