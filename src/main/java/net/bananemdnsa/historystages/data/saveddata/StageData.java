package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StageData extends SavedData {
    private final List<String> unlockedStages = new ArrayList<>();
    private static final String DATA_NAME = "historystages_global";

    public static final Set<String> SERVER_CACHE = ConcurrentHashMap.newKeySet();

    /**
     * Bumped on every change to {@link #SERVER_CACHE}, so anything derived from it can tell that
     * it went stale without being told.
     *
     * <p>A notification would have to be remembered at each of five places; a counter that lives
     * beside the data cannot be forgotten in the same way. {@code UnlockedStateGuardTest} keeps
     * the mutations inside this class, which is what makes the counter trustworthy.
     */
    private static final java.util.concurrent.atomic.AtomicLong VERSION =
            new java.util.concurrent.atomic.AtomicLong();

    /** Changes whenever the global unlocked set does. Never persisted, never sent. */
    public static long cacheVersion() {
        return VERSION.get();
    }

    /**
     * Replaces the cache with exactly these stages. The pedestal used to clear and refill
     * {@link #SERVER_CACHE} itself, which left anything derived from it holding stale data.
     */
    public static void replaceCache(java.util.Collection<String> stages) {
        SERVER_CACHE.clear();
        SERVER_CACHE.addAll(stages);
        VERSION.incrementAndGet();
    }

    public StageData() {
        SERVER_CACHE.clear();
        VERSION.incrementAndGet();
    }

    public static void refreshCache(List<String> stages) {
        Set<String> newSet = ConcurrentHashMap.newKeySet();
        newSet.addAll(stages);
        SERVER_CACHE.addAll(newSet);
        SERVER_CACHE.retainAll(newSet);
        VERSION.incrementAndGet();
    }

    public static StageData load(CompoundTag nbt, HolderLookup.Provider registries) {
        StageData data = new StageData();
        ListTag list = nbt.getList("stages", Tag.TAG_STRING);
        SERVER_CACHE.clear();
        for (int i = 0; i < list.size(); i++) {
            String stage = list.getString(i);
            data.unlockedStages.add(stage);
            SERVER_CACHE.add(stage);
        }
        VERSION.incrementAndGet();
        net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String s : unlockedStages) {
            list.add(StringTag.valueOf(s));
        }
        nbt.put("stages", list);
        return nbt;
    }

    public static StageData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            StageData data = serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(
                            new SavedData.Factory<>(StageData::new, StageData::load),
                            DATA_NAME
                    );

            refreshCache(data.unlockedStages);
            // The generation counters have no level of their own and worldgen threads must not
            // reach into data storage, so they are primed from here.
            StructureGenerationCountData.get(serverLevel);
            return data;
        }
        return new StageData();
    }

    public void addStage(String stage) {
        if (!unlockedStages.contains(stage)) {
            unlockedStages.add(stage);
            SERVER_CACHE.add(stage);
            VERSION.incrementAndGet();
            // Before the rebuild: the reset lookup needs the snapshot that still describes the
            // phase being left behind.
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.onStageLockChanged(stage, true);
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
            setDirty();
        }
    }

    public void removeStage(String stage) {
        if (unlockedStages.remove(stage)) {
            SERVER_CACHE.remove(stage);
            VERSION.incrementAndGet();
            // Before the rebuild, for the same reason as in addStage.
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.onStageLockChanged(stage, false);
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
            setDirty();
        }
    }

    public boolean hasStage(String stage) {
        return unlockedStages.contains(stage);
    }

    public List<String> getUnlockedStages() {
        return new ArrayList<>(unlockedStages);
    }
}
