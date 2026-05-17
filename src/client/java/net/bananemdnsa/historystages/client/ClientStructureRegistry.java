package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ClientStructureRegistry {
    private static final Set<String> SYNCED_STRUCTURES = new TreeSet<>();
    private static final Set<String> SYNCED_STRUCTURE_TAGS = new TreeSet<>();
    private static boolean synced;

    private static final List<String> VANILLA_STRUCTURES = List.of(
            "minecraft:ancient_city",
            "minecraft:bastion_remnant",
            "minecraft:buried_treasure",
            "minecraft:desert_pyramid",
            "minecraft:end_city",
            "minecraft:fortress",
            "minecraft:igloo",
            "minecraft:jungle_pyramid",
            "minecraft:mansion",
            "minecraft:mineshaft",
            "minecraft:monument",
            "minecraft:nether_fossil",
            "minecraft:ocean_ruin_cold",
            "minecraft:ocean_ruin_warm",
            "minecraft:pillager_outpost",
            "minecraft:ruined_portal",
            "minecraft:shipwreck",
            "minecraft:stronghold",
            "minecraft:swamp_hut",
            "minecraft:trail_ruins",
            "minecraft:trial_chambers",
            "minecraft:village_desert",
            "minecraft:village_plains",
            "minecraft:village_savanna",
            "minecraft:village_snowy",
            "minecraft:village_taiga"
    );

    private static final List<String> VANILLA_STRUCTURE_TAGS = List.of(
            "minecraft:cats_spawn_as_black",
            "minecraft:cats_spawn_in",
            "minecraft:dolphin_located",
            "minecraft:eye_of_ender_located",
            "minecraft:mineshaft",
            "minecraft:ocean_ruin",
            "minecraft:on_ocean_explorer_maps",
            "minecraft:on_treasure_maps",
            "minecraft:on_trial_chambers_maps",
            "minecraft:on_woodland_explorer_maps",
            "minecraft:ruined_portal",
            "minecraft:shipwreck",
            "minecraft:village"
    );

    private ClientStructureRegistry() {
    }

    public static synchronized void set(List<String> ids, List<String> tagIds) {
        SYNCED_STRUCTURES.clear();
        SYNCED_STRUCTURES.addAll(ids);
        SYNCED_STRUCTURE_TAGS.clear();
        SYNCED_STRUCTURE_TAGS.addAll(tagIds);
        synced = true;
    }

    public static synchronized void set(List<String> ids) {
        set(ids, List.of());
    }

    public static synchronized void clear() {
        SYNCED_STRUCTURES.clear();
        SYNCED_STRUCTURE_TAGS.clear();
        synced = false;
    }

    public static Set<String> get() {
        Set<String> values = new TreeSet<>();
        synchronized (ClientStructureRegistry.class) {
            if (synced) {
                values.addAll(SYNCED_STRUCTURES);
            }
        }
        if (values.isEmpty()) {
            values.addAll(VANILLA_STRUCTURES);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                minecraft.level.registryAccess().registry(Registries.STRUCTURE)
                        .ifPresent(registry -> registry.keySet().stream().map(ResourceLocation::toString).forEach(values::add));
            }
        }
        StageManager.getStages().values().forEach(entry ->
                entry.getStructures().stream().filter(id -> !id.startsWith("#")).forEach(values::add));
        StageManager.getIndividualStages().values().forEach(entry ->
                entry.getStructures().stream().filter(id -> !id.startsWith("#")).forEach(values::add));
        return values;
    }

    public static Set<String> getTags() {
        Set<String> values = new TreeSet<>();
        synchronized (ClientStructureRegistry.class) {
            if (synced) {
                values.addAll(SYNCED_STRUCTURE_TAGS);
            }
        }
        if (values.isEmpty()) {
            values.addAll(VANILLA_STRUCTURE_TAGS);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                minecraft.level.registryAccess().registry(Registries.STRUCTURE)
                        .ifPresent(registry -> registry.getTagNames().map(TagKey<Structure>::location)
                                .map(ResourceLocation::toString).forEach(values::add));
            }
        }
        StageManager.getStages().values().forEach(entry ->
                entry.getStructures().stream().filter(id -> id.startsWith("#")).map(id -> id.substring(1)).forEach(values::add));
        StageManager.getIndividualStages().values().forEach(entry ->
                entry.getStructures().stream().filter(id -> id.startsWith("#")).map(id -> id.substring(1)).forEach(values::add));
        return values;
    }
}
