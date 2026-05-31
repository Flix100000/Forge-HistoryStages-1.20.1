package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HistoryStages.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchPedestalBlockEntity>> RESEARCH_PEDESTAL_BE =
            BLOCK_ENTITIES.register("research_pedestal_be", () ->
                    BlockEntityType.Builder.of(ResearchPedestalBlockEntity::new,
                            ModBlocks.RESEARCH_PEDESTAL.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_2.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_3.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_4.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
