package net.bananemdnsa.historystages.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player progress for AUTO-mode individual stages.
 * Stored under the world data name {@value #DATA_NAME}.
 *
 * <p>Format: {@code Map<UUID, Map<StageId, Set<TriggerSignature>>>}. Only
 * still-locked stages have entries; on unlock the stage entry is removed.</p>
 */
public class AutoTriggerProgressData extends SavedData {

    private static final String DATA_NAME = "history_stages_auto_trigger_progress";

    // playerUuid -> stageId -> set of trigger signatures satisfied
    private final Map<UUID, Map<String, Set<Long>>> progress = new ConcurrentHashMap<>();

    public static AutoTriggerProgressData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(AutoTriggerProgressData::load, AutoTriggerProgressData::new, DATA_NAME);
        }
        return new AutoTriggerProgressData();
    }

    public static AutoTriggerProgressData load(CompoundTag nbt) {
        AutoTriggerProgressData data = new AutoTriggerProgressData();
        ListTag players = nbt.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag pTag = players.getCompound(i);
            UUID uuid = pTag.getUUID("uuid");
            Map<String, Set<Long>> perStage = new HashMap<>();
            ListTag stages = pTag.getList("stages", Tag.TAG_COMPOUND);
            for (int j = 0; j < stages.size(); j++) {
                CompoundTag sTag = stages.getCompound(j);
                String stageId = sTag.getString("id");
                long[] sigs = sTag.getLongArray("sigs");
                Set<Long> set = ConcurrentHashMap.newKeySet();
                for (long s : sigs) set.add(s);
                perStage.put(stageId, set);
            }
            data.progress.put(uuid, perStage);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Map<String, Set<Long>>> e : progress.entrySet()) {
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("uuid", e.getKey());
            ListTag stages = new ListTag();
            for (Map.Entry<String, Set<Long>> se : e.getValue().entrySet()) {
                if (se.getValue() == null || se.getValue().isEmpty()) continue;
                CompoundTag sTag = new CompoundTag();
                sTag.putString("id", se.getKey());
                long[] arr = se.getValue().stream().mapToLong(Long::longValue).toArray();
                sTag.put("sigs", new LongArrayTag(arr));
                stages.add(sTag);
            }
            if (stages.isEmpty()) continue;
            pTag.put("stages", stages);
            players.add(pTag);
        }
        nbt.put("players", players);
        return nbt;
    }

    /** Returns the (mutable) progress set for a player+stage, creating it if missing. */
    public Set<Long> getOrCreate(UUID player, String stageId) {
        return progress
                .computeIfAbsent(player, p -> new ConcurrentHashMap<>())
                .computeIfAbsent(stageId, s -> ConcurrentHashMap.newKeySet());
    }

    /** Returns the existing progress set, or null if none exists. */
    public Set<Long> peek(UUID player, String stageId) {
        Map<String, Set<Long>> perStage = progress.get(player);
        return perStage == null ? null : perStage.get(stageId);
    }

    /** Removes the progress set for one player+stage. */
    public void clear(UUID player, String stageId) {
        Map<String, Set<Long>> perStage = progress.get(player);
        if (perStage != null) {
            perStage.remove(stageId);
            if (perStage.isEmpty()) progress.remove(player);
            setDirty();
        }
    }

    /** Drops every stage entry whose stageId is not in {@code keep}. Called on stage-config reload. */
    public void pruneOrphans(Set<String> keep) {
        for (Map<String, Set<Long>> perStage : progress.values()) {
            perStage.keySet().removeIf(s -> !keep.contains(s));
        }
        progress.values().removeIf(Map::isEmpty);
        setDirty();
    }

    /** Mark dirty after external mutation (e.g. via getOrCreate -> add). */
    public void touch() {
        setDirty();
    }
}
