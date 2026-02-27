package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestialBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HistoryStages.MOD_ID);

    public static final RegistryObject<BlockEntityType<ResearchPedestialBlockEntity>> RESEARCH_PEDESTIAL_BE =
            BLOCK_ENTITIES.register("research_pedestial_be", () ->
                    BlockEntityType.Builder.of(ResearchPedestialBlockEntity::new,
                            ModBlocks.RESEARCH_PEDESTIAL.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}