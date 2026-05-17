package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.structure.ClusterBuilder;
import net.bananemdnsa.historystages.structure.ClusterDebugRenderer;
import net.bananemdnsa.historystages.structure.StructureCluster;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class StructureLockHandler {

    private static final Map<UUID, PlayerState> STATE = new HashMap<>();
    /** Radius (in chunks) to scan around the player when looking for structure starts. */
    private static final int CHUNK_SCAN_RADIUS = 8;

    private static class PlayerState {
        long lastChunkKey = Long.MIN_VALUE;
        int checkCooldown = 0;
        int damageCooldown = 0;
        int messageCooldown = 0;
        List<String> cachedLockedStructureIds = Collections.emptyList();
        List<String> cachedLockedStageIds = Collections.emptyList();
        /** Clusters near the player belonging to any locked structure (regardless of stage gating). */
        List<StructureCluster> cachedNearbyClusters = Collections.emptyList();
        /** Subset of nearby clusters whose structure is currently locked AND which contain the player. */
        List<StructureCluster> cachedActiveLockedClusters = Collections.emptyList();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;

        if (!StageManager.anyStageHasStructures()) return;

        PlayerState state = STATE.computeIfAbsent(player.getUUID(), u -> new PlayerState());

        int interval = Config.COMMON.structureCheckInterval.get();
        long chunkKey = (((long) player.chunkPosition().x) << 32) | (player.chunkPosition().z & 0xFFFFFFFFL);
        boolean chunkChanged = chunkKey != state.lastChunkKey;

        state.checkCooldown--;
        if (chunkChanged || state.checkCooldown <= 0) {
            state.checkCooldown = interval;
            state.lastChunkKey = chunkKey;
            recompute(player, state);
        }

        ClusterDebugRenderer.tick(player, state.cachedNearbyClusters, state.cachedActiveLockedClusters);

        if (state.cachedLockedStructureIds.isEmpty()) return;

        if (Config.COMMON.structureMessageEnabled.get()) {
            state.messageCooldown--;
            if (state.messageCooldown <= 0) {
                state.messageCooldown = 40;
                sendLockMessage(player, state);
            }
        }

        if (Config.COMMON.structureDamageEnabled.get() && !player.isCreative()) {
            state.damageCooldown--;
            if (state.damageCooldown <= 0) {
                state.damageCooldown = Config.COMMON.structureDamageInterval.get();
                float amount = Config.COMMON.structureDamageAmount.get().floatValue();
                player.hurt(player.level().damageSources().magic(), amount);
            }
        }
    }

    private static void recompute(ServerPlayer player, PlayerState state) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        int padding = Config.COMMON.structureLockPadding.get();
        int clusterDistance = Config.COMMON.structureClusterDistance.get();

        List<StructureCluster> nearby = ClusterBuilder.collectClustersNear(
                level, pos, CHUNK_SCAN_RADIUS, padding, clusterDistance);

        state.cachedNearbyClusters = nearby;

        if (nearby.isEmpty()) {
            state.cachedLockedStructureIds = Collections.emptyList();
            state.cachedLockedStageIds = Collections.emptyList();
            state.cachedActiveLockedClusters = Collections.emptyList();
            return;
        }

        // Determine which clusters actually contain the player.
        List<StructureCluster> active = new ArrayList<>();
        Set<String> presentIds = new HashSet<>();
        Set<String> presentTags = new HashSet<>();
        for (StructureCluster c : nearby) {
            if (!c.contains(pos)) continue;
            active.add(c);
            Holder.Reference<Structure> h = c.structure();
            h.unwrapKey().ifPresent(k -> presentIds.add(k.location().toString()));
            h.tags().forEach(t -> presentTags.add(t.location().toString()));
        }

        if (active.isEmpty()) {
            state.cachedLockedStructureIds = Collections.emptyList();
            state.cachedLockedStageIds = Collections.emptyList();
            state.cachedActiveLockedClusters = Collections.emptyList();
            return;
        }

        Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(
                player.getUUID(), Collections.emptySet());

        LinkedHashSet<String> lockedStructures = new LinkedHashSet<>();
        LinkedHashSet<String> lockedStages = new LinkedHashSet<>();

        // Global stages
        for (Map.Entry<String, StageEntry> e : StageManager.getStages().entrySet()) {
            String stageId = e.getKey();
            if (StageData.SERVER_CACHE.contains(stageId)) continue;
            List<String> entries = e.getValue().getStructures();
            if (entries == null || entries.isEmpty()) continue;
            for (String entry : entries) {
                String matched = matchEntry(entry, presentIds, presentTags);
                if (matched != null) {
                    lockedStructures.add(matched);
                    lockedStages.add(stageId);
                }
            }
        }

        // Individual stages
        for (Map.Entry<String, StageEntry> e : StageManager.getIndividualStages().entrySet()) {
            String stageId = e.getKey();
            if (playerStages.contains(stageId)) continue;
            List<String> entries = e.getValue().getStructures();
            if (entries == null || entries.isEmpty()) continue;
            for (String entry : entries) {
                String matched = matchEntry(entry, presentIds, presentTags);
                if (matched != null) {
                    lockedStructures.add(matched);
                    lockedStages.add(stageId);
                }
            }
        }

        state.cachedLockedStructureIds = new ArrayList<>(lockedStructures);
        state.cachedLockedStageIds = new ArrayList<>(lockedStages);

        // Active locked clusters = active clusters whose structure id matches one of the locked ids.
        List<StructureCluster> activeLocked = new ArrayList<>();
        for (StructureCluster c : active) {
            String id = c.structure().unwrapKey().map(k -> k.location().toString()).orElse(null);
            if (id == null) continue;
            if (lockedStructures.contains(id)) {
                activeLocked.add(c);
                continue;
            }
            // Match via tags too (entries starting with #)
            boolean tagMatched = c.structure().tags().anyMatch(t -> lockedStructures.contains("#" + t.location()));
            if (tagMatched) activeLocked.add(c);
        }
        state.cachedActiveLockedClusters = activeLocked;
    }

    private static String matchEntry(String entry, Set<String> presentIds, Set<String> presentTags) {
        if (entry == null || entry.isEmpty()) return null;
        if (entry.startsWith("#")) {
            String tag = entry.substring(1);
            return presentTags.contains(tag) ? entry : null;
        }
        return presentIds.contains(entry) ? entry : null;
    }

    /**
     * Returns ResourceLocation IDs of all structures whose start BB contains pos.
     * Kept for compatibility with {@code /history debug structure} which lists every
     * structure the player is currently inside, independent of the active lock mode.
     */
    public static List<String> collectStructureIdsAt(ServerLevel level, BlockPos pos) {
        List<Holder.Reference<Structure>> holders = collectStructureHoldersAt(level, pos);
        if (holders.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>(holders.size());
        for (Holder.Reference<Structure> h : holders) {
            h.unwrapKey().ifPresent(k -> out.add(k.location().toString()));
        }
        return out;
    }

    /**
     * Lists structures whose vanilla start bounding box contains {@code pos}. Used by the
     * structure debug command — intentionally independent of the cluster-based lock logic
     * so admins always see every structure that geometrically overlaps the position.
     */
    public static List<Holder.Reference<Structure>> collectStructureHoldersAt(ServerLevel level, BlockPos pos) {
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        net.minecraft.world.level.ChunkPos center = new net.minecraft.world.level.ChunkPos(pos);
        Set<Structure> seen = new LinkedHashSet<>();
        for (int cx = center.x - CHUNK_SCAN_RADIUS; cx <= center.x + CHUNK_SCAN_RADIUS; cx++) {
            for (int cz = center.z - CHUNK_SCAN_RADIUS; cz <= center.z + CHUNK_SCAN_RADIUS; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entry : chunk.getAllStarts().entrySet()) {
                    var start = entry.getValue();
                    if (start == null || !start.isValid()) continue;
                    if (!start.getBoundingBox().isInside(pos)) continue;
                    seen.add(entry.getKey());
                }
            }
        }
        if (seen.isEmpty()) return Collections.emptyList();
        List<Holder.Reference<Structure>> out = new ArrayList<>(seen.size());
        for (Structure s : seen) {
            var key = registry.getKey(s);
            if (key == null) continue;
            registry.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, key))
                    .ifPresent(out::add);
        }
        return out;
    }

    private static void sendLockMessage(ServerPlayer player, PlayerState state) {
        String format = Config.COMMON.structureLockMessageFormat.get();
        String structureId = state.cachedLockedStructureIds.get(0);
        String stageName = state.cachedLockedStageIds.isEmpty()
                ? structureId
                : resolveStageDisplayName(state.cachedLockedStageIds.get(0));

        String formatted = format
                .replace("{structure}", structureId)
                .replace("{stage}", stageName)
                .replace('&', '§');

        Component msg = Component.literal(formatted);

        if (Config.COMMON.structureLockInChat.get()) {
            player.sendSystemMessage(msg);
        } else {
            player.displayClientMessage(msg, true);
        }

        DebugLogger.runtime("Structure Lock", player.getName().getString(),
                "Inside locked structure '" + structureId + "' — missing stages: " + state.cachedLockedStageIds);
    }

    private static String resolveStageDisplayName(String stageId) {
        StageEntry entry = StageManager.getStages().get(stageId);
        if (entry == null) entry = StageManager.getIndividualStages().get(stageId);
        return entry != null ? entry.getDisplayName() : stageId;
    }

    public static boolean isInsideLockedStructure(Player player) {
        PlayerState state = STATE.get(player.getUUID());
        return state != null && !state.cachedLockedStructureIds.isEmpty();
    }

    public static void clearPlayer(UUID uuid) {
        STATE.remove(uuid);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;
        if (!isInsideLockedStructure(player)) return;

        BlockState blockState = event.getLevel().getBlockState(event.getPos());
        Block block = blockState.getBlock();
        boolean hasGui = block instanceof MenuProvider
                || event.getLevel().getBlockEntity(event.getPos()) instanceof MenuProvider;
        boolean isSpawner = block instanceof SpawnerBlock;

        if (!hasGui && !isSpawner) return;

        event.setCanceled(true);
    }
}
