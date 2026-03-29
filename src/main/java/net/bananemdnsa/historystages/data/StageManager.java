package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StageManager {
    private static final Map<String, StageEntry> STAGES = new HashMap<>();
    private static final List<String> LOADING_ERRORS = new ArrayList<>();
    private static final Gson GSON = new Gson();

    public static void load() {
        STAGES.clear();
        LOADING_ERRORS.clear();

        File configDir = FMLPaths.CONFIGDIR.get().resolve("historystages").toFile();
        if (!configDir.exists()) configDir.mkdirs();

        File[] files = configDir.listFiles((dir, name) ->
                name.endsWith(".json") && !name.startsWith("_"));

        if (files == null) return;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                StageEntry entry = GSON.fromJson(reader, StageEntry.class);
                String id = file.getName().replace(".json", "");
                if (entry != null) validateAndAdd(id, entry);
            } catch (Exception e) {
                LOADING_ERRORS.add("§c[Debug] Error in file: §e" + file.getName() + " §7(Invalid JSON syntax, stage skipped)");
            }
        }
    }

    private static void validateAndAdd(String stageId, StageEntry entry) {
        entry.getItems().removeIf(itemId -> {
            if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemId))) {
                LOADING_ERRORS.add("§7[Debug] §fItem §e" + itemId + " §fnot found (Stage: §b" + stageId + "§f). Skipping.");
                return true;
            }
            return false;
        });

        entry.getMods().removeIf(modId -> {
            if (!ModList.get().isLoaded(modId)) {
                LOADING_ERRORS.add("§7[Debug] §fMod §e" + modId + " §fnot found (Stage: §b" + stageId + "§f). Skipping.");
                return true;
            }
            return false;
        });

        entry.getDimensions().removeIf(dimId -> {
            if (ResourceLocation.tryParse(dimId) == null) {
                LOADING_ERRORS.add("§7[Debug] §fDimension §e" + dimId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                return true;
            }
            return false;
        });

        // Entities prüfen (attacklock)
        entry.getEntities().getAttacklock().removeIf(entityId -> {
            if (ResourceLocation.tryParse(entityId) == null) {
                LOADING_ERRORS.add("§7[Debug] §fEntity (attacklock) §e" + entityId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                return true;
            }
            return false;
        });

        // Entities prüfen (spawnlock)
        entry.getEntities().getSpawnlock().removeIf(entityId -> {
            if (ResourceLocation.tryParse(entityId) == null) {
                LOADING_ERRORS.add("§7[Debug] §fEntity (spawnlock) §e" + entityId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                return true;
            }
            return false;
        });

        if (entry.getResearchTime() <= 0) {
            LOADING_ERRORS.add("§7[Debug] §fStage §b" + stageId + " §fhas no 'research_time' defined. Using global config default.");
        }

        STAGES.put(stageId, entry);
        System.out.println("[HistoryStages] Stage geladen: " + stageId);
    }

    public static List<String> getLoadingErrors() { return LOADING_ERRORS; }
    public static void reloadStages() { load(); }
    public static Map<String, StageEntry> getStages() { return STAGES; }

    public static String getStageForItemOrMod(String itemId, String modId) {
        for (var entry : STAGES.entrySet()) {
            StageEntry data = entry.getValue();
            if (data.getItems() != null && data.getItems().contains(itemId)) return entry.getKey();
            if (data.getMods() != null && data.getMods().contains(modId)) return entry.getKey();
            if (data.getTags() != null) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                if (item != null) {
                    for (String tagId : data.getTags()) {
                        var tagKey = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
                        if (item.builtInRegistryHolder().is(tagKey)) return entry.getKey();
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
            if (entry.getValue().getDimensions() != null && entry.getValue().getDimensions().contains(dimensionId))
                return entry.getKey();
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
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));

        for (Map.Entry<String, StageEntry> entry : STAGES.entrySet()) {
            StageEntry data = entry.getValue();
            boolean match = data.getItems().contains(itemId);
            if (!match) match = data.getMods().contains(modId);
            if (!match && item != null && data.getTags() != null) {
                for (String tagId : data.getTags()) {
                    var tagKey = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
                    if (item.builtInRegistryHolder().is(tagKey)) { match = true; break; }
                }
            }
            if (match) allFoundStages.add(entry.getKey());
        }
        return allFoundStages;
    }

    public static int getResearchTimeInTicks(String stageId) {
        StageEntry entry = STAGES.get(stageId);
        if (entry != null && entry.getResearchTime() > 0) return entry.getResearchTime() * 20;
        return net.bananemdnsa.historystages.Config.COMMON.researchTimeInSeconds.get() * 20;
    }

    public static boolean isItemLockedForServer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return false;
        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace());
        if (requiredStages.isEmpty()) return false;
        for (String stage : requiredStages) {
            if (!net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(stage)) return true;
        }
        return false;
    }

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

    public static boolean isItemLocked(ItemStack stack, boolean isClientSide) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return false;
        List<String> requiredStages = getAllStagesForItemOrMod(res.toString(), res.getNamespace());
        if (requiredStages.isEmpty()) return false;
        for (String stage : requiredStages) {
            if (isClientSide) {
                if (!net.bananemdnsa.historystages.util.ClientStageCache.isStageUnlocked(stage)) return true;
            } else {
                if (!net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(stage)) return true;
            }
        }
        return false;
    }
}
