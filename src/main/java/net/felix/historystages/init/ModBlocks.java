package net.felix.historystages.init;

import net.felix.historystages.HistoryStages;
import net.felix.historystages.block.ResearchStationBlock; // WICHTIGER IMPORT
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HistoryStages.MOD_ID);

    // Geändert: Benutzt jetzt "new ResearchStationBlock" statt "new Block"
    public static final RegistryObject<Block> RESEARCH_STATION = BLOCKS.register("research_station",
            () -> new ResearchStationBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0f)
                    .requiresCorrectToolForDrops()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}