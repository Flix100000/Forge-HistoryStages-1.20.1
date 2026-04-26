package net.bananemdnsa.historystages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;

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
            // Create global stages directory
            Path globalPath = CONFIG_PATH.resolve("global");
            if (!Files.exists(globalPath)) {
                Files.createDirectories(globalPath);
            }

            // Create individual stages directory
            Path individualPath = CONFIG_PATH.resolve("individual");
            if (!Files.exists(individualPath)) {
                Files.createDirectories(individualPath);
            }

            // Create _exampleStage.json in global directory
            File exampleFile = new File(globalPath.toFile(), "_exampleStage.json");
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

        // Research Time (optional, in seconds. If omitted or 0, uses global config default)
        json.addProperty("research_time", 20);

        // Items Category
        JsonArray items = new JsonArray();
        items.add("minecraft:iron_ingot");
        items.add("minecraft:netherite_sword");
        json.add("items", items);

        // Tags Category
        JsonArray tags = new JsonArray();
        tags.add("c:ores/iron");
        tags.add("minecraft:logs");
        json.add("tags", tags);

        // Mods Category
        JsonArray mods = new JsonArray();
        mods.add("lootr");
        mods.add("mekanism");
        json.add("mods", mods);

        // Mod Exceptions Category
        JsonArray modExceptions = new JsonArray();
        modExceptions.add("mekanism:creative_energy_cube");
        json.add("mod_exceptions", modExceptions);

        // Recipes Category
        JsonArray recipes = new JsonArray();
        recipes.add("minecraft:diamond_sword");
        json.add("recipes", recipes);

        // Dimensions Category
        JsonArray dimensions = new JsonArray();
        dimensions.add("minecraft:the_nether");
        dimensions.add("minecraft:the_end");
        json.add("dimensions", dimensions);

        // Structures Category
        JsonArray structures = new JsonArray();
        structures.add("minecraft:stronghold");
        structures.add("#minecraft:village");
        json.add("structures", structures);

        // Entities Category (with subcategories)
        JsonObject entities = new JsonObject();
        JsonArray attacklock = new JsonArray();
        attacklock.add("minecraft:zombie");
        entities.add("attacklock", attacklock);
        JsonArray spawnlock = new JsonArray();
        spawnlock.add("minecraft:skeleton");
        entities.add("spawnlock", spawnlock);
        json.add("entities", entities);

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
