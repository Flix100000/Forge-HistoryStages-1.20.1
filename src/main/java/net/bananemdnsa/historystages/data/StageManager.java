package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StageManager {
    private static final Map<String, StageEntry> STAGES = new HashMap<>();
    private static final List<String> LOADING_ERRORS = new ArrayList<>(); // NEU
    private static final Gson GSON = new Gson();

    public static void load() {
        STAGES.clear();
        LOADING_ERRORS.clear(); // Liste leeren beim Neuladen

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
                LOADING_ERRORS.add("§c[Debug] Error in file: §e" + file.getName() + " §7(Invalid JSON syntax, stage skipped)");
            }
        }
    }

    private static void validateAndAdd(String stageId, StageEntry entry) {
        // Items prüfen und ungültige entfernen
        entry.getItems().removeIf(itemId -> {
            if (!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId))) {
                LOADING_ERRORS.add("§7[Debug] §fItem §e" + itemId + " §fnot found (Stage: §b" + stageId + "§f). Skipping.");
                return true;
            }
            return false;
        });

        // Mods prüfen
        entry.getMods().removeIf(modId -> {
            if (!ModList.get().isLoaded(modId)) {
                LOADING_ERRORS.add("§7[Debug] §fMod §e" + modId + " §fnot found (Stage: §b" + stageId + "§f). Skipping.");
                return true;
            }
            return false;
        });

        // Dimensionen prüfen
        entry.getDimensions().removeIf(dimId -> {
            if (!ResourceLocation.isValidResourceLocation(dimId)) {
                LOADING_ERRORS.add("§7[Debug] §fDimension §e" + dimId + " §finvalid (Stage: §b" + stageId + "§f). Skipping.");
                return true;
            }
            return false;
        });

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

    public static String getStageForDimension(String dimensionId) {
        for (var entry : STAGES.entrySet()) {
            StageEntry data = entry.getValue();
            if (data.getDimensions() != null && data.getDimensions().contains(dimensionId)) {
                return entry.getKey();
            }
        }
        return null;
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


}