package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.MultiBlockTier3Block;
import net.bananemdnsa.historystages.block.MultiBlockTier4Block;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.bananemdnsa.historystages.block.ResearchPedestalTier2Block;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, HistoryStages.MOD_ID);

    private static BlockBehaviour.Properties pedestalProps() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .strength(5.0f)
                .requiresCorrectToolForDrops();
    }

    public static final DeferredHolder<Block, Block> RESEARCH_PEDESTAL = BLOCKS.register("research_pedestal",
            () -> new ResearchPedestalBlock(pedestalProps()));

    public static final DeferredHolder<Block, Block> RESEARCH_PEDESTAL_TIER_2 = BLOCKS.register("research_pedestal_tier_2",
            () -> new ResearchPedestalTier2Block(pedestalProps()));

    public static final DeferredHolder<Block, Block> RESEARCH_PEDESTAL_TIER_3 = BLOCKS.register("research_pedestal_tier_3",
            () -> new MultiBlockTier3Block(pedestalProps()));

    public static final DeferredHolder<Block, Block> RESEARCH_PEDESTAL_TIER_4 = BLOCKS.register("research_pedestal_tier_4",
            () -> new MultiBlockTier4Block(pedestalProps()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
