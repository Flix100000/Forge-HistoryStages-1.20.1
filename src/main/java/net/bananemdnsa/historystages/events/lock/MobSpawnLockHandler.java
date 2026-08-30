package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class MobSpawnLockHandler {

    /**
     * Source-aware spawn locking for mobs. Maps the vanilla {@link MobSpawnType}
     * to one of our 6 buckets and cancels the spawn if any required stage is missing.
     */
    /**
     * Dimension ids as strings, kept per level key.
     *
     * <p>{@code dimension().location().toString()} builds a new string every call, and the three
     * handlers below ask for it per spawn - which on EntityJoinLevel means per arrow, per dropped
     * item, per XP orb. There are only ever a handful of dimensions.
     */
    private static final Map<ResourceKey<Level>, String> DIMENSION_IDS = new ConcurrentHashMap<>();

    private static String dimensionId(Level level) {
        return DIMENSION_IDS.computeIfAbsent(level.dimension(), key -> key.location().toString());
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!StageLocks.engine().anyEntitySpawnLocks()) return;

        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (entityType == null) return;

        String source = mapSpawnSource(event.getSpawnType());
        String dimension = dimensionId(event.getLevel().getLevel());
        List<String> requiredStageIds = StageLocks.engine()
                .gatingStagesForEntitySpawn(entityType.toString(), source, dimension, StageScope.GLOBAL);
        if (requiredStageIds.isEmpty()) return;

        if (LockResolution.isLocked(requiredStageIds, StageLocks.serverGlobal())) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }

    /**
     * Vanilla breeding does not fire {@link FinalizeSpawnEvent} — babies are added directly via
     * {@code Level.addFreshEntity}. NeoForge fires {@link BabyEntitySpawnEvent} instead, which
     * is the right hook for the "breeding" source bucket.
     */
    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!StageLocks.engine().anyEntitySpawnLocks()) return;
        if (event.getChild() == null) return;
        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getChild().getType());
        if (entityType == null) return;

        String dimension = dimensionId(event.getParentA().level());
        List<String> requiredStageIds = StageLocks.engine()
                .gatingStagesForEntitySpawn(entityType.toString(), "breeding", dimension, StageScope.GLOBAL);
        if (requiredStageIds.isEmpty()) return;

        if (LockResolution.isLocked(requiredStageIds, StageLocks.serverGlobal())) {
            event.setCanceled(true);
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
        // Before anything else, including the two toString calls below: this fires for every
        // arrow, dropped item, XP orb and falling block in the world.
        if (!StageLocks.engine().anyEntitySpawnLocks()) return;
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob) return;

        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (entityType == null) return;

        // For non-mob entities we treat any matching entry as a full block (subject to dimension filter).
        String dimension = dimensionId(event.getLevel());
        List<String> requiredStageIds = StageLocks.engine()
                .gatingStagesWithSpawnEntry(entityType.toString(), dimension, StageScope.GLOBAL);
        if (requiredStageIds.isEmpty()) return;

        if (LockResolution.isLocked(requiredStageIds, StageLocks.serverGlobal())) {
            event.setCanceled(true);
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
