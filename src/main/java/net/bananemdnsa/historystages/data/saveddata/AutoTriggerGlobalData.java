package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide shared progress pool for AUTO-mode global stages.
 * Any player contributing a sub-trigger counts toward the same set.
 */
public class AutoTriggerGlobalData extends SavedData {

    private static final String DATA_NAME = "history_stages_auto_trigger_global";

    private final Map<String, Set<Long>> progress = new ConcurrentHashMap<>();

    public static AutoTriggerGlobalData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(AutoTriggerGlobalData::load, AutoTriggerGlobalData::new, DATA_NAME);
        }
        return new AutoTriggerGlobalData();
    }

    public static AutoTriggerGlobalData load(CompoundTag nbt) {
        AutoTriggerGlobalData data = new AutoTriggerGlobalData();
        ListTag stages = nbt.getList("stages", Tag.TAG_COMPOUND);
        for (int i = 0; i < stages.size(); i++) {
            CompoundTag sTag = stages.getCompound(i);
            String stageId = sTag.getString("id");
            long[] sigs = sTag.getLongArray("sigs");
            Set<Long> set = ConcurrentHashMap.newKeySet();
            for (long s : sigs) set.add(s);
            data.progress.put(stageId, set);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag stages = new ListTag();
        for (Map.Entry<String, Set<Long>> e : progress.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            CompoundTag sTag = new CompoundTag();
            sTag.putString("id", e.getKey());
            long[] arr = e.getValue().stream().mapToLong(Long::longValue).toArray();
            sTag.put("sigs", new LongArrayTag(arr));
            stages.add(sTag);
        }
        nbt.put("stages", stages);
        return nbt;
    }

    /** Returns the (mutable) progress set for a stage, creating it if missing. */
    public Set<Long> getOrCreate(String stageId) {
        return progress.computeIfAbsent(stageId, s -> ConcurrentHashMap.newKeySet());
    }

    /** Returns the existing progress set, or null if none exists. */
    public Set<Long> peek(String stageId) {
        return progress.get(stageId);
    }

    /** Removes the progress set for one stage. */
    public void clear(String stageId) {
        progress.remove(stageId);
        setDirty();
    }

    /** Drops every stage entry whose stageId is not in {@code keep}. Called on stage-config reload. */
    public void pruneOrphans(Set<String> keep) {
        progress.keySet().removeIf(s -> !keep.contains(s));
        setDirty();
    }

    /** Mark dirty after external mutation (e.g. via getOrCreate -> add). */
    public void touch() {
        setDirty();
    }
}
