package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.BiomeEffectRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
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

/**
 * Punishes players standing in a biome belonging to a stage they don't own yet.
 *
 * <p>Unlike the structure lock there is no geometry to scan: a biome is a direct lookup on the
 * chunk. The per-player cache exists to avoid re-walking every stage entry each tick, and is
 * refreshed whenever the player crosses into a new biome cell.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class BiomeLockHandler {

    private static final Map<UUID, PlayerState> STATE = new HashMap<>();

    private static class PlayerState {
        long lastCellKey = Long.MIN_VALUE;
        int checkCooldown = 0;
        int damageCooldown = 0;
        int messageCooldown = 0;
        /** Biome IDs and {@code #tags} listed by any stage this player is missing. */
        Set<String> lockedEntries = Collections.emptySet();
        /** The player's current biome, when it is locked — empty otherwise. */
        List<String> currentLockedBiomeIds = Collections.emptyList();
        List<String> currentLockedStageIds = Collections.emptyList();
        /** Previous tick's locked state, so leaving a biome can be detected exactly once. */
        boolean wasLocked = false;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;

        if (!StageManager.anyStageHasBiomes()) return;

        PlayerState state = STATE.computeIfAbsent(player.getUUID(), u -> new PlayerState());

        // Biomes are stored per 4x4x4 cell, so the cell key is the exact granularity at which
        // the answer can change. The interval is only a fallback so stage edits take effect
        // for a player who is standing still.
        BlockPos pos = player.blockPosition();
        long cellKey = cellKey(pos);
        boolean cellChanged = cellKey != state.lastCellKey;

        state.checkCooldown--;
        if (cellChanged || state.checkCooldown <= 0) {
            state.checkCooldown = Config.COMMON.biomeCheckInterval.get();
            state.lastCellKey = cellKey;
            recompute(player, state);
        }

        boolean locked = !state.currentLockedBiomeIds.isEmpty();

        if (state.wasLocked && !locked && Config.COMMON.biomeClearEffectsOnLeave.get()) {
            clearEffects(player);
        }
        state.wasLocked = locked;

        if (!locked) return;

        if (Config.COMMON.biomeEffectsEnabled.get()) {
            applyEffects(player);
        }

        if (Config.COMMON.biomeMessageEnabled.get()) {
            state.messageCooldown--;
            if (state.messageCooldown <= 0) {
                state.messageCooldown = 40;
                sendLockMessage(player, state);
            }
        }

        if (Config.COMMON.biomeDamageEnabled.get() && !player.isCreative()) {
            state.damageCooldown--;
            if (state.damageCooldown <= 0) {
                state.damageCooldown = Config.COMMON.biomeDamageInterval.get();
                float amount = Config.COMMON.biomeDamageAmount.get().floatValue();
                player.hurt(player.level().damageSources().magic(), amount);
            }
        }
    }

    private static long cellKey(BlockPos pos) {
        long x = (pos.getX() >> 2) & 0x1FFFFFL;
        long y = (pos.getY() >> 2) & 0x3FFL;
        long z = (pos.getZ() >> 2) & 0x1FFFFFL;
        return (x << 32) | (z << 11) | y;
    }

    /**
     * Rebuilds both the player's locked-entry set (used to judge arbitrary target positions)
     * and the verdict for the biome they are standing in right now.
     */
    private static void recompute(ServerPlayer player, PlayerState state) {
        BiomeView here = viewOf(player.level(), player.blockPosition());

        Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(
                player.getUUID(), Collections.emptySet());

        Set<String> lockedEntries = new HashSet<>();
        LinkedHashSet<String> currentBiomes = new LinkedHashSet<>();
        LinkedHashSet<String> currentStages = new LinkedHashSet<>();

        for (Map.Entry<String, StageEntry> e : StageManager.getStages().entrySet()) {
            if (StageData.SERVER_CACHE.contains(e.getKey())) continue;
            collect(e.getKey(), e.getValue(), here, lockedEntries, currentBiomes, currentStages);
        }
        for (Map.Entry<String, StageEntry> e : StageManager.getIndividualStages().entrySet()) {
            if (playerStages.contains(e.getKey())) continue;
            collect(e.getKey(), e.getValue(), here, lockedEntries, currentBiomes, currentStages);
        }

        state.lockedEntries = lockedEntries;
        state.currentLockedBiomeIds = new ArrayList<>(currentBiomes);
        state.currentLockedStageIds = new ArrayList<>(currentStages);
    }

    /**
     * Note that {@code mod_linked} is deliberately not consulted here. Like the structure lock's
     * equivalent, it is editor bookkeeping recording which entries came from the mod popup — the
     * IDs themselves are already in the biome list, so matching against it would be redundant.
     */
    private static void collect(String stageId, StageEntry entry, BiomeView here,
                                Set<String> lockedEntries,
                                Set<String> currentBiomes, Set<String> currentStages) {
        List<String> entries = entry.getBiomes();
        if (entries.isEmpty()) return;

        lockedEntries.addAll(entries);

        if (!matches(here, entries)) return;

        if (here.id() != null) currentBiomes.add(here.id());
        currentStages.add(stageId);
    }

    /** The identity of one biome, flattened once so matching doesn't re-walk the holder. */
    private record BiomeView(String id, Set<String> tags) {}

    private static BiomeView viewOf(LevelReader level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        String id = holder.unwrapKey().map(k -> k.location().toString()).orElse(null);
        Set<String> tags = new HashSet<>();
        holder.tags().forEach(t -> tags.add(t.location().toString()));
        return new BiomeView(id, tags);
    }

    private static boolean matches(BiomeView view, List<String> entries) {
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) continue;
            if (entry.startsWith("#")) {
                if (view.tags().contains(entry.substring(1))) return true;
            } else if (entry.equals(view.id())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCached(BiomeView view, PlayerState state) {
        if (view.id() != null && state.lockedEntries.contains(view.id())) return true;
        for (String tag : view.tags()) {
            if (state.lockedEntries.contains("#" + tag)) return true;
        }
        return false;
    }

    // --- Effects ---

    /**
     * Applies every configured effect, refreshing an instance only once it has burned through
     * half its duration. Applying every tick would push an effect packet to the client each
     * time; this keeps it to roughly one packet per effect per half-duration.
     */
    private static void applyEffects(ServerPlayer player) {
        for (BiomeEffectRegistry.BiomeEffect spec : BiomeEffectRegistry.all()) {
            MobEffectInstance existing = player.getEffect(spec.effect());
            if (existing != null
                    && existing.getAmplifier() >= spec.amplifier()
                    && existing.getDuration() > spec.durationTicks() / 2) {
                continue;
            }
            player.addEffect(new MobEffectInstance(spec.effect(), spec.durationTicks(), spec.amplifier()));
        }
    }

    private static void clearEffects(ServerPlayer player) {
        for (BiomeEffectRegistry.BiomeEffect spec : BiomeEffectRegistry.all()) {
            player.removeEffect(spec.effect());
        }
    }

    // --- Messaging ---

    private static void sendLockMessage(ServerPlayer player, PlayerState state) {
        String format = Config.COMMON.biomeLockMessageFormat.get();
        String biomeId = state.currentLockedBiomeIds.isEmpty() ? "?" : state.currentLockedBiomeIds.get(0);
        String stageName = state.currentLockedStageIds.isEmpty()
                ? biomeId
                : resolveStageDisplayName(state.currentLockedStageIds.get(0));

        String formatted = format
                .replace("{biome}", biomeId)
                .replace("{stage}", stageName)
                .replace('&', '§');

        Component msg = Component.literal(formatted);

        if (Config.COMMON.biomeLockInChat.get()) {
            player.sendSystemMessage(msg);
        } else {
            player.displayClientMessage(msg, true);
        }

        DebugLogger.runtime("Biome Lock", player.getName().getString(),
                "Inside locked biome '" + biomeId + "' — missing stages: " + state.currentLockedStageIds);
    }

    private static String resolveStageDisplayName(String stageId) {
        StageEntry entry = StageManager.getStages().get(stageId);
        if (entry == null) entry = StageManager.getIndividualStages().get(stageId);
        return entry != null ? entry.getDisplayName() : stageId;
    }

    // --- Queries used by the interaction handlers ---

    public static boolean isInsideLockedBiome(Player player) {
        PlayerState state = STATE.get(player.getUUID());
        return state != null && !state.currentLockedBiomeIds.isEmpty();
    }

    /**
     * True when the biome at {@code pos} is locked for this player. Anti-cheese counterpart to
     * {@link #isInsideLockedBiome}: standing on the savanna side of the border shouldn't let a
     * player mine into the desert they haven't unlocked.
     */
    public static boolean isBlockInLockedBiome(Player player, BlockPos pos) {
        PlayerState state = STATE.get(player.getUUID());
        if (state == null || state.lockedEntries.isEmpty()) return false;
        return matchesCached(viewOf(player.level(), pos), state);
    }

    public static void clearPlayer(UUID uuid) {
        STATE.remove(uuid);
    }

    /**
     * Force every tracked player to recompute on the next tick. Called from the packet handlers
     * whose payload changes which biomes are locked (toggle, save, delete) — otherwise a player
     * standing still keeps the pre-change verdict until the interval elapses.
     */
    public static void invalidateAll() {
        for (PlayerState s : STATE.values()) {
            s.checkCooldown = 0;
            s.lastCellKey = Long.MIN_VALUE;
        }
    }

    // --- Interaction blocking ---

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (shouldBlockInteraction(event.getEntity(), true, false, event.getPos())) {
            cancelBlockClick(event);
            return;
        }
        // Placements where the clicked block sits outside but the resulting placement target is
        // inside: buckets, TNT, blocks placed against the border.
        BlockPos placePos = event.getPos().relative(event.getFace());
        if (shouldBlockInteraction(event.getEntity(), true, false, placePos)) {
            cancelBlockClick(event);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (shouldBlockInteraction(event.getEntity(), true, false, null)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            forceInventoryResync(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (shouldBlockInteraction(event.getEntity(), true, false, event.getTarget().blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            forceInventoryResync(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (shouldBlockInteraction(event.getEntity(), true, false, event.getTarget().blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            forceInventoryResync(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (shouldBlockInteraction(event.getEntity(), false, true, event.getPos())) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (shouldBlockInteraction(event.getEntity(), false, true, event.getTarget().blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (shouldBlockInteraction(event.getPlayer(), false, true, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * The client already predicted the placement / item consumption locally, and a FAIL result
     * doesn't reliably undo that — so push the full inventory state back. Same reasoning as the
     * structure lock.
     */
    private static void cancelBlockClick(PlayerInteractEvent.RightClickBlock event) {
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        forceInventoryResync(event.getEntity());
    }

    private static void forceInventoryResync(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.containerMenu.broadcastFullState();
        }
    }

    private static boolean shouldBlockInteraction(Player player, boolean right, boolean left, BlockPos targetPos) {
        if (player == null) return false;
        if (player.level().isClientSide()) return false;
        if (!(player instanceof ServerPlayer sp)) return false;
        if (sp.isSpectator()) return false;

        if (right && !Config.COMMON.biomeBlockRightClick.get()) return false;
        if (left && !Config.COMMON.biomeBlockLeftClick.get()) return false;

        if (isInsideLockedBiome(sp)) return true;
        return targetPos != null && isBlockInLockedBiome(sp, targetPos);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide()) return;
        if (!Config.COMMON.biomeBlockProjectiles.get()) return;
        if (!(event.getProjectile().getOwner() instanceof ServerPlayer shooter)) return;

        Vec3 hit = event.getRayTraceResult().getLocation();
        BlockPos hitPos = BlockPos.containing(hit.x, hit.y, hit.z);
        if (!isBlockInLockedBiome(shooter, hitPos)) return;

        event.setCanceled(true);
        event.getProjectile().discard();
    }

    /**
     * Removes blocks inside a locked biome from the explosion's affected list. Explosions have
     * no owner worth attributing, so this checks every tracked player's cached lock set — a
     * biome locked for anyone present is protected.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (STATE.isEmpty()) return;
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        List<PlayerState> active = new ArrayList<>();
        for (PlayerState s : STATE.values()) {
            if (!s.lockedEntries.isEmpty()) active.add(s);
        }
        if (active.isEmpty()) return;

        Level level = event.getLevel();
        affected.removeIf(pos -> {
            BiomeView view = viewOf(level, pos);
            for (PlayerState s : active) {
                if (matchesCached(view, s)) return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity().getUUID());
    }
}
