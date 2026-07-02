package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobSpawnLockHandler {

    /**
     * Source-aware spawn locking for mobs. Maps the vanilla {@link MobSpawnType}
     * to one of our 6 buckets and cancels the spawn if any required stage is missing.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (entityType == null) return;

        String source = mapSpawnSource(event.getSpawnType());
        String dimension = event.getLevel().getLevel().dimension().location().toString();
        List<String> requiredStageIds = StageManager.getAllStagesForSpawnLockedEntity(entityType.toString(), source, dimension);
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
     * Vanilla breeding does not fire {@link MobSpawnEvent.FinalizeSpawn} — babies are added directly
     * via {@code Level.addFreshEntity}. Forge fires {@link BabyEntitySpawnEvent} instead, which is
     * the right hook for the "breeding" source bucket.
     */
    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (event.getChild() == null) return;
        ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(event.getChild().getType());
        if (entityType == null) return;

        String dimension = event.getParentA().level().dimension().location().toString();
        List<String> requiredStageIds = StageManager.getAllStagesForSpawnLockedEntity(entityType.toString(), "breeding", dimension);
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
     * go through {@link MobSpawnEvent.FinalizeSpawn}. Only "block all sources" entries apply here,
     * since selective entries are conceptually about mob spawn reasons.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob) return;

        ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (entityType == null) return;

        // For non-mob entities we treat any matching entry as a full block (subject to dimension filter).
        String dimension = event.getLevel().dimension().location().toString();
        List<String> requiredStageIds = StageManager.getAllStagesWithSpawnlockEntry(entityType.toString(), dimension);
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
            case SPAWNER, TRIGGERED, MOB_SUMMONED -> "spawner";
            case STRUCTURE, PATROL, EVENT -> "structure";
            case BREEDING, CONVERSION, REINFORCEMENT, JOCKEY -> "breeding";
            case COMMAND -> "summon";
            case SPAWN_EGG, BUCKET, DISPENSER -> "spawn_egg";
        };
    }
}
