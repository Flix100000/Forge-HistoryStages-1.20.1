package net.bananemdnsa.historystages.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class RegistryHelper {

    public static ResourceLocation getResourceLocationFromRegistry(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    public static ResourceLocation getResourceLocationFromRegistry(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block);
    }

    public List<Item> getItemsByMod(String modId) {
        return ForgeRegistries.ITEMS.getEntries().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(modId))
                .map(entry -> entry.getValue())
                .toList();
    }
}
