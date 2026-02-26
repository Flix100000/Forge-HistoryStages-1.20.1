package net.felix.historystages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("historystages");

    public static void setupConfig() {
        try {
            // 1. Create directory if not exists
            if (!Files.exists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_PATH);
            }

            // 2. Create _exampleStage.json
            // The underscore ensures that the StageManager ignores this file during loading
            File exampleFile = new File(CONFIG_PATH.toFile(), "_exampleStage.json");
            if (!exampleFile.exists()) {
                createExampleJson(exampleFile);
            }
        } catch (IOException e) {
            System.err.println("Could not create config directory: " + e.getMessage());
        }
    }

    private static void createExampleJson(File file) {
        JsonObject json = new JsonObject();

        // Display Name
        json.addProperty("display_name", "Example Stage");

        // Items Category
        JsonArray items = new JsonArray();
        items.add("minecraft:iron_ingot");
        items.add("minecraft:netherite_sword");
        json.add("items", items);

        // Tags Category
        JsonArray tags = new JsonArray();
        tags.add("forge:ores/iron");
        tags.add("minecraft:logs");
        json.add("tags", tags);

        // Mods Category
        JsonArray mods = new JsonArray();
        mods.add("lootr");
        mods.add("mekanism");
        json.add("mods", mods);

        // Dimensions Category (NEW)
        JsonArray dimensions = new JsonArray();
        dimensions.add("minecraft:the_nether");
        dimensions.add("minecraft:the_end");
        json.add("dimensions", dimensions);

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}