package net.bananemdnsa.historystages.data.saveddata;

import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.temporary.TemporaryConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the live state of {@code mode=temporary} stages: how many game ticks
 * remain before a currently-unlocked stage re-locks, how long until a re-locked
 * stage may be triggered again, and how many times each stage has already been
 * unlocked (against its {@code max_triggers} quota).
 *
 * <p>Timers are measured in game ticks. Global timers advance once per
 * {@value #TICK_STEP} ticks of server uptime; individual timers advance only
 * while the owning player is online, so an offline player's timer is frozen.</p>
 *
 * <p>On expiry the stage is re-locked via {@link StageStates}. Re-trigger
 * eligibility (see {@link #isGlobalEligible} / {@link #isIndividualEligible}) is
 * consulted by {@code AutoTriggerManager} before it unlocks a temporary stage: a
 * stage is blocked while its timer or cooldown runs, and permanently once its
 * unlock count reaches {@code max_triggers}.</p>
 */
public class TemporaryStageData extends SavedData {

    private static final String DATA_NAME = "historystages_temporary";

    /** Game ticks between timer decrements (1 second). Decrement amount matches. */
    public static final int TICK_STEP = 20;

    // --- global state ---
    private final Map<String, Long> globalActive = new ConcurrentHashMap<>();   // stageId → ticks until re-lock
    private final Map<String, Long> globalCooldown = new ConcurrentHashMap<>(); // stageId → ticks until re-triggerable
    private final Map<String, Integer> globalCount = new ConcurrentHashMap<>(); // stageId → unlocks performed

    // --- per-player state ---
    private final Map<UUID, Map<String, Long>> playerActive = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> playerCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> playerCount = new ConcurrentHashMap<>();

    public static TemporaryStageData get(Level level) {
        if (level instanceof ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage()
                    .computeIfAbsent(
                            new SavedData.Factory<>(TemporaryStageData::new, TemporaryStageData::load),
                            DATA_NAME
                    );
        }
        return new TemporaryStageData();
    }

    // ====================================================================
    // Eligibility (consulted by AutoTriggerManager before unlocking)
    // ====================================================================

    /**
     * True iff a global temporary stage may currently be triggered/unlocked.
     * @param maxTriggers the stage's {@code max_triggers} (0 = unlimited).
     */
    public boolean isGlobalEligible(String stageId, int maxTriggers) {
        if (globalActive.containsKey(stageId)) return false;
        if (globalCooldown.containsKey(stageId)) return false;
        return maxTriggers <= 0 || getGlobalCount(stageId) < maxTriggers;
    }

    /** True iff an individual temporary stage may currently be triggered/unlocked for this player. */
    public boolean isIndividualEligible(UUID player, String stageId, int maxTriggers) {
        Map<String, Long> active = playerActive.get(player);
        if (active != null && active.containsKey(stageId)) return false;
        Map<String, Long> cd = playerCooldown.get(player);
        if (cd != null && cd.containsKey(stageId)) return false;
        return maxTriggers <= 0 || getIndividualCount(player, stageId) < maxTriggers;
    }

    // ====================================================================
    // Unlock counts
    // ====================================================================

    public int getGlobalCount(String stageId) {
        return globalCount.getOrDefault(stageId, 0);
    }

    public void setGlobalCount(String stageId, int count) {
        if (count <= 0) globalCount.remove(stageId);
        else globalCount.put(stageId, count);
        setDirty();
    }

    public int getIndividualCount(UUID player, String stageId) {
        Map<String, Integer> m = playerCount.get(player);
        return m == null ? 0 : m.getOrDefault(stageId, 0);
    }

    public void setIndividualCount(UUID player, String stageId, int count) {
        if (count <= 0) {
            Map<String, Integer> m = playerCount.get(player);
            if (m != null) { m.remove(stageId); if (m.isEmpty()) playerCount.remove(player); }
        } else {
            playerCount.computeIfAbsent(player, p -> new ConcurrentHashMap<>()).put(stageId, count);
        }
        setDirty();
    }

    // ====================================================================
    // Start timers (called on unlock by AutoTriggerManager)
    // ====================================================================

    public void startGlobalTimer(String stageId, long durationTicks) {
        globalActive.put(stageId, Math.max(TICK_STEP, durationTicks));
        globalCount.merge(stageId, 1, Integer::sum);
        setDirty();
    }

    public void startIndividualTimer(UUID player, String stageId, long durationTicks) {
        playerActive.computeIfAbsent(player, p -> new ConcurrentHashMap<>())
                .put(stageId, Math.max(TICK_STEP, durationTicks));
        playerCount.computeIfAbsent(player, p -> new ConcurrentHashMap<>())
                .merge(stageId, 1, Integer::sum);
        setDirty();
    }

    // ====================================================================
    // Tick — decrement timers and cooldowns, re-lock on expiry
    // ====================================================================

    /**
     * Advances all timers. Call every server tick; only acts every
     * {@value #TICK_STEP} ticks. {@code lookup} resolves a stage id to its
     * config so expiry knows the cooldown and remaining-unlock quota.
     */
    public void tick(MinecraftServer server, int tickCounter, TempConfigLookup lookup) {
        if (server == null || tickCounter % TICK_STEP != 0) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        tickGlobal(overworld, lookup);
        tickIndividual(server, lookup);
    }

    private void tickGlobal(ServerLevel level, TempConfigLookup lookup) {
        if (!globalActive.isEmpty()) {
            List<String> expired = new ArrayList<>();
            for (Map.Entry<String, Long> e : globalActive.entrySet()) {
                long remaining = e.getValue() - TICK_STEP;
                if (remaining <= 0) {
                    expired.add(e.getKey());
                } else {
                    e.setValue(remaining);
                }
            }
            for (String stageId : expired) {
                globalActive.remove(stageId);
                StageStates.relockGlobal(stageId, level);
                long cd = cooldownIfMoreUnlocksLeft(lookup, stageId, getGlobalCount(stageId));
                if (cd > 0) globalCooldown.put(stageId, cd);
            }
            setDirty();
        }

        if (!globalCooldown.isEmpty()) {
            globalCooldown.entrySet().removeIf(e -> {
                long remaining = e.getValue() - TICK_STEP;
                if (remaining <= 0) return true;
                e.setValue(remaining);
                return false;
            });
            setDirty();
        }
    }

    private void tickIndividual(MinecraftServer server, TempConfigLookup lookup) {
        if (playerActive.isEmpty() && playerCooldown.isEmpty()) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();

            Map<String, Long> active = playerActive.get(uuid);
            if (active != null && !active.isEmpty()) {
                List<String> expired = new ArrayList<>();
                for (Map.Entry<String, Long> e : active.entrySet()) {
                    long remaining = e.getValue() - TICK_STEP;
                    if (remaining <= 0) {
                        expired.add(e.getKey());
                    } else {
                        e.setValue(remaining);
                    }
                }
                for (String stageId : expired) {
                    active.remove(stageId);
                    StageStates.relockIndividual(stageId, player);
                    long cd = cooldownIfMoreUnlocksLeft(lookup, stageId, getIndividualCount(uuid, stageId));
                    if (cd > 0) {
                        playerCooldown.computeIfAbsent(uuid, p -> new ConcurrentHashMap<>()).put(stageId, cd);
                    }
                }
                if (active.isEmpty()) playerActive.remove(uuid);
                setDirty();
            }

            Map<String, Long> cd = playerCooldown.get(uuid);
            if (cd != null && !cd.isEmpty()) {
                cd.entrySet().removeIf(e -> {
                    long remaining = e.getValue() - TICK_STEP;
                    if (remaining <= 0) return true;
                    e.setValue(remaining);
                    return false;
                });
                if (cd.isEmpty()) playerCooldown.remove(uuid);
                setDirty();
            }
        }
    }

    /**
     * Ends a running individual timer ahead of schedule, applying the same
     * bookkeeping {@link #tickIndividual} performs on natural expiry: the active
     * timer is dropped and the configured cooldown starts (unless the stage has
     * used up its quota). The unlock count is deliberately left alone — the
     * unlock was spent, and ending it early does not refund it.
     *
     * <p>Re-locking the stage itself is the caller's job; this only touches
     * timer state. No running timer means this is a no-op, so callers that
     * don't know the stage's mode can call it unconditionally.</p>
     */
    public void expireIndividualEarly(UUID player, String stageId, TempConfigLookup lookup) {
        Map<String, Long> active = playerActive.get(player);
        if (active == null || active.remove(stageId) == null) return;
        if (active.isEmpty()) playerActive.remove(player);

        long cd = cooldownIfMoreUnlocksLeft(lookup, stageId, getIndividualCount(player, stageId));
        if (cd > 0) {
            playerCooldown.computeIfAbsent(player, p -> new ConcurrentHashMap<>()).put(stageId, cd);
        }
        setDirty();
    }

    /**
     * Returns the cooldown ticks to apply after a re-lock, or 0 if the stage has
     * exhausted its unlock quota (no point cooling down a stage that can never
     * trigger again) or has no cooldown configured.
     */
    private long cooldownIfMoreUnlocksLeft(TempConfigLookup lookup, String stageId, int countSoFar) {
        TemporaryConfig cfg = lookup.get(stageId);
        if (cfg == null) return 0;
        boolean moreLeft = cfg.isUnlimited() || countSoFar < cfg.getMaxTriggers();
        return moreLeft ? cfg.cooldownTicks() : 0;
    }

    /** Resolves a stage id to its {@link TemporaryConfig}. */
    @FunctionalInterface
    public interface TempConfigLookup {
        TemporaryConfig get(String stageId);
    }

    // ====================================================================
    // Maintenance
    // ====================================================================

    /** Drops every entry whose stageId is not in {@code keep}. Called on stage-config reload. */
    public void pruneOrphans(Set<String> keep) {
        globalActive.keySet().removeIf(s -> !keep.contains(s));
        globalCooldown.keySet().removeIf(s -> !keep.contains(s));
        globalCount.keySet().removeIf(s -> !keep.contains(s));
        for (Map<String, Long> m : playerActive.values()) m.keySet().removeIf(s -> !keep.contains(s));
        for (Map<String, Long> m : playerCooldown.values()) m.keySet().removeIf(s -> !keep.contains(s));
        for (Map<String, Integer> m : playerCount.values()) m.keySet().removeIf(s -> !keep.contains(s));
        playerActive.values().removeIf(Map::isEmpty);
        playerCooldown.values().removeIf(Map::isEmpty);
        playerCount.values().removeIf(Map::isEmpty);
        setDirty();
    }

    /** Clears all temporary state for one global stage (timer, cooldown, unlock count). */
    public void clearGlobal(String stageId) {
        globalActive.remove(stageId);
        globalCooldown.remove(stageId);
        globalCount.remove(stageId);
        setDirty();
    }

    /** Clears all temporary state for one individual stage+player. */
    public void clearIndividual(UUID player, String stageId) {
        Map<String, Long> active = playerActive.get(player);
        if (active != null) { active.remove(stageId); if (active.isEmpty()) playerActive.remove(player); }
        Map<String, Long> cd = playerCooldown.get(player);
        if (cd != null) { cd.remove(stageId); if (cd.isEmpty()) playerCooldown.remove(player); }
        Map<String, Integer> cnt = playerCount.get(player);
        if (cnt != null) { cnt.remove(stageId); if (cnt.isEmpty()) playerCount.remove(player); }
        setDirty();
    }

    /** Remaining unlock-timer ticks for a global stage, or -1 if not currently active. */
    public long globalActiveTicks(String stageId) {
        return globalActive.getOrDefault(stageId, -1L);
    }

    /** Remaining cooldown ticks for a global stage, or -1 if not in cooldown. */
    public long globalCooldownTicks(String stageId) {
        return globalCooldown.getOrDefault(stageId, -1L);
    }

    public long individualActiveTicks(UUID player, String stageId) {
        Map<String, Long> m = playerActive.get(player);
        return m == null ? -1L : m.getOrDefault(stageId, -1L);
    }

    public long individualCooldownTicks(UUID player, String stageId) {
        Map<String, Long> m = playerCooldown.get(player);
        return m == null ? -1L : m.getOrDefault(stageId, -1L);
    }

    // ====================================================================
    // Persistence
    // ====================================================================

    public static TemporaryStageData load(CompoundTag nbt, HolderLookup.Provider registries) {
        TemporaryStageData data = new TemporaryStageData();
        readTickMap(nbt.getList("global_active", Tag.TAG_COMPOUND), data.globalActive);
        readTickMap(nbt.getList("global_cooldown", Tag.TAG_COMPOUND), data.globalCooldown);
        readCountMap(nbt.getList("global_count", Tag.TAG_COMPOUND), data.globalCount);

        ListTag players = nbt.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag pTag = players.getCompound(i);
            UUID uuid = pTag.getUUID("uuid");
            Map<String, Long> active = new ConcurrentHashMap<>();
            readTickMap(pTag.getList("active", Tag.TAG_COMPOUND), active);
            if (!active.isEmpty()) data.playerActive.put(uuid, active);
            Map<String, Long> cd = new ConcurrentHashMap<>();
            readTickMap(pTag.getList("cooldown", Tag.TAG_COMPOUND), cd);
            if (!cd.isEmpty()) data.playerCooldown.put(uuid, cd);
            Map<String, Integer> cnt = new ConcurrentHashMap<>();
            readCountMap(pTag.getList("count", Tag.TAG_COMPOUND), cnt);
            if (!cnt.isEmpty()) data.playerCount.put(uuid, cnt);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.put("global_active", writeTickMap(globalActive));
        nbt.put("global_cooldown", writeTickMap(globalCooldown));
        nbt.put("global_count", writeCountMap(globalCount));

        ListTag players = new ListTag();
        Set<UUID> uuids = new HashSet<>();
        uuids.addAll(playerActive.keySet());
        uuids.addAll(playerCooldown.keySet());
        uuids.addAll(playerCount.keySet());
        for (UUID uuid : uuids) {
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("uuid", uuid);
            pTag.put("active", writeTickMap(playerActive.getOrDefault(uuid, Map.of())));
            pTag.put("cooldown", writeTickMap(playerCooldown.getOrDefault(uuid, Map.of())));
            pTag.put("count", writeCountMap(playerCount.getOrDefault(uuid, Map.of())));
            players.add(pTag);
        }
        nbt.put("players", players);
        return nbt;
    }

    private static void readTickMap(ListTag list, Map<String, Long> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            into.put(e.getString("id"), e.getLong("ticks"));
        }
    }

    private static ListTag writeTickMap(Map<String, Long> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> e : map.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.getKey());
            t.putLong("ticks", e.getValue());
            list.add(t);
        }
        return list;
    }

    private static void readCountMap(ListTag list, Map<String, Integer> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            into.put(e.getString("id"), e.getInt("count"));
        }
    }

    private static ListTag writeCountMap(Map<String, Integer> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.getKey());
            t.putInt("count", e.getValue());
            list.add(t);
        }
        return list;
    }
}
