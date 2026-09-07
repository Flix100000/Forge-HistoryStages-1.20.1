package net.bananemdnsa.historystages.data.saveddata;

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

    // --- NEU: DER CACHE ---
    // Das Mixin greift hierauf zu, weil es keinen direkten Zugriff auf "SavedData" hat
    public static final Set<String> SERVER_CACHE = ConcurrentHashMap.newKeySet();

    /**
     * Bumped on every change to {@link #SERVER_CACHE}, so anything derived from it can tell that
     * it went stale without being told.
     *
     * <p>A notification would have to be remembered at each of the places that write the cache; a
     * counter that lives beside the data cannot be forgotten in the same way.
     */
    private static final java.util.concurrent.atomic.AtomicLong VERSION =
            new java.util.concurrent.atomic.AtomicLong();

    /** Changes whenever the global unlocked set does. Never persisted, never sent. */
    public static long cacheVersion() {
        return VERSION.get();
    }

    public StageData() {
        // Falls das Objekt neu erstellt wird, stellen wir sicher, dass der Cache leer ist
        SERVER_CACHE.clear();
        VERSION.incrementAndGet();
    }

    public static StageData load(CompoundTag nbt) {
        StageData data = new StageData();
        ListTag list = nbt.getList("stages", Tag.TAG_STRING);
        SERVER_CACHE.clear(); // Cache leeren beim Laden
        for (int i = 0; i < list.size(); i++) {
            String stage = list.getString(i);
            data.unlockedStages.add(stage);
            SERVER_CACHE.add(stage); // CACHE BEIM LADEN FÜLLEN
        }
        VERSION.incrementAndGet();
        net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (String s : unlockedStages) {
            list.add(StringTag.valueOf(s));
        }
        nbt.put("stages", list);
        return nbt;
    }

    /**
     * Replaces the cache contents atomically: adds new entries first, then removes stale ones.
     * This avoids the brief empty-cache window that clear()+addAll() would cause.
     */
    public static void refreshCache(List<String> stages) {
        Set<String> newSet = ConcurrentHashMap.newKeySet();
        newSet.addAll(stages);
        // Compared before the swap, and the counter only moves when the set really is different.
        // This runs on every StageData.get(), which happens many times a tick, and a caller that
        // throws its work away whenever the counter moves — the recipe gate does — would then
        // rebuild constantly. Nothing derived from the cache can be stale while the cache is
        // unchanged, so saying nothing here is not a shortcut.
        boolean changed = !SERVER_CACHE.equals(newSet);
        SERVER_CACHE.addAll(newSet);
        SERVER_CACHE.retainAll(newSet);
        if (changed) VERSION.incrementAndGet();
    }

    public static StageData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            StageData data = serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(StageData::load, StageData::new, DATA_NAME);

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
            SERVER_CACHE.add(stage); // CACHE AKTUALISIEREN
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
            SERVER_CACHE.remove(stage); // AUS CACHE ENTFERNEN
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