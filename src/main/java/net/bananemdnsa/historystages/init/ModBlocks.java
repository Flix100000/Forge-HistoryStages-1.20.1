package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    public static final Block RESEARCH_PEDESTAL = new ResearchPedestalBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(3.5F)
            .sound(SoundType.STONE)
            .lightLevel(state -> 0));

    private ModBlocks() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, HistoryStages.id("research_pedestal"), RESEARCH_PEDESTAL);
    }

    public static BlockItem createPedestalItem() {
        return new BlockItem(RESEARCH_PEDESTAL, new Item.Properties());
    }
}
