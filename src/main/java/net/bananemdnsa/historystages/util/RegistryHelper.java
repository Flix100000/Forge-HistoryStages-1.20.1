package net.bananemdnsa.historystages.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

public class RegistryHelper {

    public static ResourceLocation getResourceLocationFromRegistry(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    public static ResourceLocation getResourceLocationFromRegistry(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block);
    }
}
