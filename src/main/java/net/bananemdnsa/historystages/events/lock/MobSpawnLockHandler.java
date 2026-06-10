package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.List;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class MobSpawnLockHandler {

    /**
     * Source-aware spawn locking for mobs. Maps the vanilla {@link MobSpawnType}
     * to one of our 6 buckets and cancels the spawn if any required stage is missing.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (entityType == null) return;

        String source = mapSpawnSource(event.getSpawnType());
        List<String> requiredStageIds = StageManager.getAllStagesForSpawnLockedEntity(entityType.toString(), source);
        if (requiredStageIds.isEmpty()) return;

        for (String stageId : requiredStageIds) {
            if (!StageData.SERVER_CACHE.contains(stageId)) {
                event.setSpawnCancelled(true);
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Vanilla breeding does not fire {@link FinalizeSpawnEvent} — babies are added directly via
     * {@code Level.addFreshEntity}. NeoForge fires {@link BabyEntitySpawnEvent} instead, which
     * is the right hook for the "breeding" source bucket.
     */
    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (event.getChild() == null) return;
        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getChild().getType());
        if (entityType == null) return;

        List<String> requiredStageIds = StageManager.getAllStagesForSpawnLockedEntity(entityType.toString(), "breeding");
        if (requiredStageIds.isEmpty()) return;

        for (String stageId : requiredStageIds) {
            if (!StageData.SERVER_CACHE.contains(stageId)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Fallback for non-Mob entities (items, projectiles, boats, paintings, …) which never
     * go through {@link FinalizeSpawnEvent}. Only "block all sources" entries apply here,
     * since selective entries are conceptually about mob spawn reasons.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob) return;

        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (entityType == null) return;

        // For non-mob entities we treat any matching entry as a full block.
        List<String> requiredStageIds = StageManager.getAllStagesWithSpawnlockEntry(entityType.toString());
        if (requiredStageIds.isEmpty()) return;

        for (String stageId : requiredStageIds) {
            if (!StageData.SERVER_CACHE.contains(stageId)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static String mapSpawnSource(MobSpawnType type) {
        return switch (type) {
            case NATURAL, CHUNK_GENERATION -> "natural";
            case SPAWNER, TRIAL_SPAWNER, TRIGGERED, MOB_SUMMONED -> "spawner";
            case STRUCTURE, PATROL, EVENT -> "structure";
            case BREEDING, CONVERSION, REINFORCEMENT, JOCKEY -> "breeding";
            case COMMAND -> "summon";
            case SPAWN_EGG, BUCKET, DISPENSER -> "spawn_egg";
        };
    }
}
