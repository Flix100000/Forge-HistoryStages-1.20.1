package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StructureLockEvents {
    private static final Map<UUID, PlayerState> STATE = new HashMap<>();
    private static final int CHUNK_SCAN_RADIUS = 8;

    private StructureLockEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!StageManager.anyStageHasStructures()) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayer(player);
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !isInsideLockedStructure(serverPlayer)) {
                return InteractionResult.PASS;
            }
            BlockState state = world.getBlockState(hitResult.getBlockPos());
            Block block = state.getBlock();
            boolean hasGui = block instanceof MenuProvider || world.getBlockEntity(hitResult.getBlockPos()) instanceof MenuProvider;
            boolean isSpawner = block instanceof SpawnerBlock;
            if (hasGui || isSpawner) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    public static boolean isInsideLockedStructure(ServerPlayer player) {
        PlayerState state = STATE.get(player.getUUID());
        return state != null && !state.cachedLockedStructureIds.isEmpty();
    }

    private static void tickPlayer(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }

        PlayerState state = STATE.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
        long chunkKey = (((long) player.chunkPosition().x) << 32) | (player.chunkPosition().z & 0xFFFFFFFFL);
        boolean changedChunk = chunkKey != state.lastChunkKey;
        state.checkCooldown--;
        if (changedChunk || state.checkCooldown <= 0) {
            state.checkCooldown = Config.COMMON.structureCheckInterval;
            state.lastChunkKey = chunkKey;
            recomputeLockedStructures(player, state);
        }

        if (state.cachedLockedStructureIds.isEmpty()) {
            return;
        }

        if (Config.COMMON.structureMessageEnabled) {
            state.messageCooldown--;
            if (state.messageCooldown <= 0) {
                state.messageCooldown = 40;
                sendLockMessage(player, state);
            }
        }

        if (Config.COMMON.structureDamageEnabled && !player.isCreative()) {
            state.damageCooldown--;
            if (state.damageCooldown <= 0) {
                state.damageCooldown = Config.COMMON.structureDamageInterval;
                player.hurt(player.level().damageSources().magic(), Config.COMMON.structureDamageAmount);
            }
        }
    }

    private static void recomputeLockedStructures(ServerPlayer player, PlayerState state) {
        List<Holder.Reference<Structure>> holders = collectStructureHoldersAt(player.serverLevel(), player.blockPosition());
        if (holders.isEmpty()) {
            state.cachedLockedStructureIds = Collections.emptyList();
            state.cachedLockedStageIds = Collections.emptyList();
            return;
        }

        Set<String> presentIds = new HashSet<>();
        Set<String> presentTags = new HashSet<>();
        for (Holder.Reference<Structure> holder : holders) {
            holder.unwrapKey().ifPresent(key -> presentIds.add(key.location().toString()));
            holder.tags().forEach(tag -> presentTags.add(tag.location().toString()));
        }

        Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(player.getUUID(), Collections.emptySet());
        LinkedHashSet<String> lockedStructures = new LinkedHashSet<>();
        LinkedHashSet<String> lockedStages = new LinkedHashSet<>();

        scanStructureEntries(StageManager.getStages(), StageData.SERVER_CACHE, presentIds, presentTags, lockedStructures, lockedStages);
        scanStructureEntries(StageManager.getIndividualStages(), playerStages, presentIds, presentTags, lockedStructures, lockedStages);

        state.cachedLockedStructureIds = new ArrayList<>(lockedStructures);
        state.cachedLockedStageIds = new ArrayList<>(lockedStages);
    }

    private static void scanStructureEntries(Map<String, StageEntry> stages, Set<String> unlocked, Set<String> presentIds,
                                             Set<String> presentTags, Set<String> lockedStructures, Set<String> lockedStages) {
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            if (unlocked.contains(entry.getKey())) {
                continue;
            }
            for (String structure : entry.getValue().getStructures()) {
                String matched = matchEntry(structure, presentIds, presentTags);
                if (matched != null) {
                    lockedStructures.add(matched);
                    lockedStages.add(entry.getKey());
                }
            }
        }
    }

    private static String matchEntry(String entry, Set<String> presentIds, Set<String> presentTags) {
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        if (entry.startsWith("#")) {
            String tag = entry.substring(1);
            return presentTags.contains(tag) ? entry : null;
        }
        return presentIds.contains(entry) ? entry : null;
    }

    private static void sendLockMessage(ServerPlayer player, PlayerState state) {
        String structureId = state.cachedLockedStructureIds.getFirst();
        String stageName = state.cachedLockedStageIds.isEmpty() ? structureId : resolveStageDisplayName(state.cachedLockedStageIds.getFirst());
        String message = Config.COMMON.structureLockMessageFormat
                .replace("{structure}", structureId)
                .replace("{stage}", stageName);
        Component component = Component.literal(message);
        if (Config.COMMON.structureLockInChat) {
            player.sendSystemMessage(component);
        } else {
            player.displayClientMessage(component, true);
        }
        DebugLogger.runtime("Structure Lock", player.getName().getString(),
                "Inside locked structure '" + structureId + "' - missing stages: " + state.cachedLockedStageIds);
    }

    private static String resolveStageDisplayName(String stageId) {
        StageEntry entry = StageManager.getStages().get(stageId);
        if (entry == null) {
            entry = StageManager.getIndividualStages().get(stageId);
        }
        return entry != null ? entry.getDisplayName() : stageId;
    }

    public static List<Holder.Reference<Structure>> collectStructureHoldersAt(ServerLevel level, BlockPos pos) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<Structure> found = new LinkedHashSet<>();
        try {
            Map<Structure, ?> all = level.structureManager().getAllStructuresAt(pos);
            found.addAll(all.keySet());
        } catch (Throwable ignored) {
        }

        ChunkPos center = new ChunkPos(pos);
        for (int cx = center.x - CHUNK_SCAN_RADIUS; cx <= center.x + CHUNK_SCAN_RADIUS; cx++) {
            for (int cz = center.z - CHUNK_SCAN_RADIUS; cz <= center.z + CHUNK_SCAN_RADIUS; cz++) {
                ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) {
                        found.add(entry.getKey());
                    }
                }
            }
        }

        List<Holder.Reference<Structure>> holders = new ArrayList<>();
        for (Structure structure : found) {
            var key = registry.getKey(structure);
            if (key != null) {
                registry.getHolder(ResourceKey.create(Registries.STRUCTURE, key)).ifPresent(holders::add);
            }
        }
        return holders;
    }

    private static final class PlayerState {
        long lastChunkKey = Long.MIN_VALUE;
        int checkCooldown;
        int damageCooldown;
        int messageCooldown;
        List<String> cachedLockedStructureIds = Collections.emptyList();
        List<String> cachedLockedStageIds = Collections.emptyList();
    }
}
