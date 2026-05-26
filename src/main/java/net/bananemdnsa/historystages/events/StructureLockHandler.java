package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncLockBordersPacket;
import net.bananemdnsa.historystages.structure.ClusterBuilder;
import net.bananemdnsa.historystages.structure.ClusterDebugRenderer;
import net.bananemdnsa.historystages.structure.StructureCluster;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
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
        /** Nearby clusters whose structure is actually locked — input set for the per-tick fast containment check. */
        List<StructureCluster> cachedLockedNearby = Collections.emptyList();
        /** Per-cluster mapping of which stage IDs that cluster contributes to (filled by recompute, read by fast update). */
        Map<StructureCluster, Set<String>> cachedClusterStages = Collections.emptyMap();
        /** Subset of locked-nearby clusters that contain the player right now. */
        List<StructureCluster> cachedActiveLockedClusters = Collections.emptyList();
        /** Hash of the last-sent border BB list, to skip redundant network sends. */
        int lastBorderHash = 0;
        /** Last known outside-the-zone position (for wall-mode bounce-back). Null until first tick. */
        Vec3 lastSafePos = null;
        /** Whether the player was inside a locked zone on the previous tick (for wall-mode transition detection). */
        boolean lastTickInside = false;
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

        // Wall-mode bounce runs BEFORE recompute so the player is teleported out before any
        // damage/message handlers see them as "inside". Uses the previous tick's cached zones —
        // safe because zones change only via recompute (chunk-change or interval).
        if (Config.COMMON.structureWallMode.get()) {
            applyWallMode(player, state);
        }

        state.checkCooldown--;
        if (chunkChanged || state.checkCooldown <= 0) {
            state.checkCooldown = interval;
            state.lastChunkKey = chunkKey;
            recompute(player, state);
        } else {
            // Cheap per-tick containment recheck so the lock state reacts within one tick
            // when the player crosses a zone boundary inside an already-scanned area, instead
            // of waiting up to checkInterval ticks for the next full recompute.
            fastContainmentUpdate(player, state);
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
            state.cachedLockedNearby = Collections.emptyList();
            state.cachedClusterStages = Collections.emptyMap();
            syncBordersIfChanged(player, state, Collections.emptyList());
            return;
        }

        // Collect structure IDs/tags from ALL nearby clusters so we can match locked
        // entries even when the player is just close to (not inside) a structure.
        Set<String> presentIds = new HashSet<>();
        Set<String> presentTags = new HashSet<>();
        for (StructureCluster c : nearby) {
            Holder.Reference<Structure> h = c.structure();
            h.unwrapKey().ifPresent(k -> presentIds.add(k.location().toString()));
            h.tags().forEach(t -> presentTags.add(t.location().toString()));
        }

        Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(
                player.getUUID(), Collections.emptySet());

        LinkedHashSet<String> lockedStructures = new LinkedHashSet<>();
        LinkedHashSet<String> lockedStages = new LinkedHashSet<>();

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

        // Locked clusters near the player + their per-cluster stage contribution. We compute
        // (cluster -> stages) here once and cache it so the per-tick fast containment update
        // can derive activeStageIds without re-iterating every stage entry.
        List<StructureCluster> lockedNearby = new ArrayList<>();
        Map<StructureCluster, Set<String>> clusterStages = new HashMap<>();
        for (StructureCluster c : nearby) {
            if (!structureMatchesLocked(c, lockedStructures)) continue;
            lockedNearby.add(c);

            Set<String> idView = new HashSet<>();
            c.structure().unwrapKey().ifPresent(k -> idView.add(k.location().toString()));
            Set<String> tagView = new HashSet<>();
            c.structure().tags().forEach(t -> tagView.add(t.location().toString()));

            Set<String> stages = new LinkedHashSet<>();
            for (Map.Entry<String, StageEntry> e : StageManager.getStages().entrySet()) {
                if (!lockedStages.contains(e.getKey())) continue;
                List<String> entries = e.getValue().getStructures();
                if (entries == null) continue;
                for (String entry : entries) {
                    if (matchEntry(entry, idView, tagView) != null) {
                        stages.add(e.getKey());
                        break;
                    }
                }
            }
            for (Map.Entry<String, StageEntry> e : StageManager.getIndividualStages().entrySet()) {
                if (!lockedStages.contains(e.getKey())) continue;
                List<String> entries = e.getValue().getStructures();
                if (entries == null) continue;
                for (String entry : entries) {
                    if (matchEntry(entry, idView, tagView) != null) {
                        stages.add(e.getKey());
                        break;
                    }
                }
            }
            clusterStages.put(c, stages);
        }

        state.cachedLockedNearby = lockedNearby;
        state.cachedClusterStages = clusterStages;

        // Derive the active (containing) subset + per-cluster stage IDs.
        applyContainment(player.blockPosition(), state);

        // Send the full lockShape geometry to the client. The renderer culls interior faces
        // (where a shape's face is occluded by another shape) so the force-field texture
        // traces the actual silhouette of the orange union, not box-by-box.
        List<BoundingBox> borderShapes = new ArrayList<>();
        for (StructureCluster c : lockedNearby) borderShapes.addAll(c.lockShapes());
        syncBordersIfChanged(player, state, borderShapes);
    }

    /**
     * Per-tick recheck of which locked-nearby clusters contain the player, using the cached
     * cluster list and the per-cluster stage mapping built by {@link #recompute}. No structure
     * scan, no stage-entry iteration — just one {@code contains(pos)} per locked cluster.
     */
    private static void fastContainmentUpdate(ServerPlayer player, PlayerState state) {
        if (state.cachedLockedNearby.isEmpty()) return;
        applyContainment(player.blockPosition(), state);
    }

    /**
     * Wall-mode enforcement: bounces the player back to the last safe outside position when
     * they cross a zone boundary INWARDS. Inside→outside and inside→inside moves are allowed
     * so a player who was already trapped (logged out inside, then admin locked the stage)
     * can still move around and escape; only fresh entry from outside is blocked.
     */
    private static void applyWallMode(ServerPlayer player, PlayerState state) {
        Vec3 currentPos = player.position();
        boolean insideNow = isPosInsideAnyLock(player.blockPosition(), state);

        if (state.lastSafePos == null) {
            // First-tick init: don't bounce regardless of where we are.
            if (!insideNow) state.lastSafePos = currentPos;
            state.lastTickInside = insideNow;
            return;
        }

        if (insideNow && !state.lastTickInside) {
            // Crossed from outside to inside this tick. Teleport back to the saved safe spot.
            player.teleportTo(state.lastSafePos.x, state.lastSafePos.y, state.lastSafePos.z);
            // Don't update lastSafePos / lastTickInside — after teleport the player is back outside.
            return;
        }

        if (!insideNow) {
            state.lastSafePos = currentPos;
        }
        state.lastTickInside = insideNow;
    }

    private static boolean isPosInsideAnyLock(BlockPos pos, PlayerState state) {
        for (StructureCluster c : state.cachedLockedNearby) {
            if (c.contains(pos)) return true;
        }
        return false;
    }

    /**
     * True when the given block position lies inside any locked cluster cached for this player.
     * Used by the anti-cheese block-pos checks: a player standing outside a zone shouldn't be
     * able to mine into a village, interact with a chest poking through a wall, etc.
     */
    public static boolean isBlockInLockedZone(Player player, BlockPos pos) {
        PlayerState state = STATE.get(player.getUUID());
        if (state == null) return false;
        return isPosInsideAnyLock(pos, state);
    }

    private static void applyContainment(BlockPos pos, PlayerState state) {
        List<StructureCluster> active = new ArrayList<>();
        LinkedHashSet<String> activeStructureIds = new LinkedHashSet<>();
        LinkedHashSet<String> activeStageIds = new LinkedHashSet<>();
        for (StructureCluster c : state.cachedLockedNearby) {
            if (!c.contains(pos)) continue;
            active.add(c);
            c.structure().unwrapKey().ifPresent(k -> activeStructureIds.add(k.location().toString()));
            Set<String> stages = state.cachedClusterStages.get(c);
            if (stages != null) activeStageIds.addAll(stages);
        }
        state.cachedActiveLockedClusters = active;
        state.cachedLockedStructureIds = new ArrayList<>(activeStructureIds);
        state.cachedLockedStageIds = new ArrayList<>(activeStageIds);
    }

    private static boolean structureMatchesLocked(StructureCluster c, Set<String> lockedStructures) {
        String id = c.structure().unwrapKey().map(k -> k.location().toString()).orElse(null);
        if (id != null && lockedStructures.contains(id)) return true;
        return c.structure().tags().anyMatch(t -> lockedStructures.contains("#" + t.location()));
    }

    private static void syncBordersIfChanged(ServerPlayer player, PlayerState state, List<BoundingBox> shapes) {
        int hash = computeBoxHash(shapes);
        if (hash == state.lastBorderHash) return;
        state.lastBorderHash = hash;
        PacketHandler.sendLockBordersToPlayer(new SyncLockBordersPacket(shapes), player);
    }

    private static int computeBoxHash(List<BoundingBox> shapes) {
        int h = shapes.size();
        for (BoundingBox b : shapes) {
            h = h * 31 + b.minX();
            h = h * 31 + b.minY();
            h = h * 31 + b.minZ();
            h = h * 31 + b.maxX();
            h = h * 31 + b.maxY();
            h = h * 31 + b.maxZ();
        }
        return h;
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

    /**
     * Blanket-cancels every right-click interaction while the player is inside a locked
     * structure (blocks, item self-use, entity interactions). Gated by
     * {@code structureBlockRightClick}.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (shouldBlockInteraction(event.getEntity(), true, false, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (shouldBlockInteraction(event.getEntity(), true, false, null)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (shouldBlockInteraction(event.getEntity(), true, false, event.getTarget().blockPosition())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (shouldBlockInteraction(event.getEntity(), true, false, event.getTarget().blockPosition())) event.setCanceled(true);
    }

    /**
     * Blanket-cancels every left-click interaction while the player is inside a locked
     * structure (attacking entities, starting/continuing block-break). Gated by
     * {@code structureBlockLeftClick}.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (shouldBlockInteraction(event.getEntity(), false, true, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (shouldBlockInteraction(event.getEntity(), false, true, event.getTarget().blockPosition())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (shouldBlockInteraction(event.getPlayer(), false, true, event.getPos())) event.setCanceled(true);
    }

    /**
     * Shared gate for every interaction handler. Triggers when the player is inside a locked
     * zone OR when the target block/entity is inside one (anti-cheese: prevents mining or
     * reaching into a zone from outside). Plus the per-side config flag must be on.
     */
    private static boolean shouldBlockInteraction(Player player, boolean right, boolean left, BlockPos targetPos) {
        if (player == null) return false;
        if (player.level().isClientSide()) return false;
        if (!(player instanceof ServerPlayer sp)) return false;
        if (sp.isSpectator()) return false;

        boolean playerInside = isInsideLockedStructure(sp);
        boolean targetInside = targetPos != null && isBlockInLockedZone(sp, targetPos);
        if (!playerInside && !targetInside) return false;

        if (right && !Config.COMMON.structureBlockRightClick.get()) return false;
        if (left && !Config.COMMON.structureBlockLeftClick.get()) return false;
        return true;
    }

    /**
     * Cancels and discards projectiles whose impact would land inside a locked zone. Uses
     * the shooter's cached lock geometry — projectiles without a player owner (e.g. dispenser
     * arrows) are not checked. Gated by {@code structureBlockProjectiles}.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide()) return;
        if (!Config.COMMON.structureBlockProjectiles.get()) return;
        if (!(event.getProjectile().getOwner() instanceof ServerPlayer shooter)) return;

        PlayerState state = STATE.get(shooter.getUUID());
        if (state == null || state.cachedLockedNearby.isEmpty()) return;

        Vec3 hit = event.getRayTraceResult().getLocation();
        BlockPos hitPos = BlockPos.containing(hit.x, hit.y, hit.z);
        if (!isPosInsideAnyLock(hitPos, state)) return;

        event.setCanceled(true);
        event.getProjectile().discard();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Prevents the per-UUID STATE map from growing forever as players cycle through.
        clearPlayer(event.getEntity().getUUID());
    }
}
