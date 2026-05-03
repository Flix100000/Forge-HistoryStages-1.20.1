package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static BlockEntityType<ResearchPedestalBlockEntity> RESEARCH_PEDESTAL;

    private ModBlockEntities() {
    }

    public static void register() {
        RESEARCH_PEDESTAL = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, HistoryStages.id("research_pedestal"),
                FabricBlockEntityTypeBuilder.create(ResearchPedestalBlockEntity::new, ModBlocks.RESEARCH_PEDESTAL).build());
    }
}
