package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class MobSpawnLockEvents {
    private MobSpawnLockEvents() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world.isClientSide() || entity instanceof net.minecraft.server.level.ServerPlayer) {
                return;
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id == null) {
                return;
            }
            for (String stageId : StageManager.getAllStagesForSpawnLockedEntity(id.toString())) {
                if (!StageData.SERVER_CACHE.contains(stageId)) {
                    entity.discard();
                    return;
                }
            }
        });
    }
}
