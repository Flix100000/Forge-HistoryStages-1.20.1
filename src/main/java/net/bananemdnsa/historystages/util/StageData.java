package net.bananemdnsa.historystages.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.bananemdnsa.historystages.ftbquests.OptionalFTBQuestsHooks;

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

    public StageData() {
        // Falls das Objekt neu erstellt wird, stellen wir sicher, dass der Cache leer ist
        SERVER_CACHE.clear();
    }

    public static StageData load(CompoundTag nbt, HolderLookup.Provider provider) {
        StageData data = new StageData();
        ListTag list = nbt.getList("stages", Tag.TAG_STRING);
        SERVER_CACHE.clear(); // Cache leeren beim Laden
        for (int i = 0; i < list.size(); i++) {
            String stage = list.getString(i);
            data.unlockedStages.add(stage);
            SERVER_CACHE.add(stage); // CACHE BEIM LADEN FÜLLEN
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
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
        SERVER_CACHE.addAll(newSet);
        SERVER_CACHE.retainAll(newSet);
    }

    public static StageData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            StageData data = serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(new Factory<>(StageData::new, StageData::load, DataFixTypes.LEVEL), DATA_NAME);

            refreshCache(data.unlockedStages);

            return data;
        }
        return new StageData();
    }

    public void addStage(String stage) {
        if (!unlockedStages.contains(stage)) {
            unlockedStages.add(stage);
            SERVER_CACHE.add(stage); // CACHE AKTUALISIEREN
            OptionalFTBQuestsHooks.globalUnlocked(stage);
            setDirty();
            String displayName = lookupDisplayName(stage, false);
            net.bananemdnsa.historystages.api.StageEvents.UNLOCKED.invoker().onChanged(stage, displayName);

            net.minecraft.server.MinecraftServer server = net.bananemdnsa.historystages.util.ServerHolder.get();
            if (server != null) {
                var entry = net.bananemdnsa.historystages.data.StageManager.getStages().get(stage);
                String icon = entry != null ? entry.getIcon() : null;
                net.bananemdnsa.historystages.network.Networking.sendStageUnlockedToastToAll(server, displayName, icon);
            }
        }
    }

    public void removeStage(String stage) {
        if (unlockedStages.remove(stage)) {
            SERVER_CACHE.remove(stage); // AUS CACHE ENTFERNEN
            setDirty();
            net.bananemdnsa.historystages.api.StageEvents.LOCKED.invoker().onChanged(stage, lookupDisplayName(stage, false));
        }
    }

    private static String lookupDisplayName(String stageId, boolean individual) {
        var entry = individual
                ? net.bananemdnsa.historystages.data.StageManager.getIndividualStages().get(stageId)
                : net.bananemdnsa.historystages.data.StageManager.getStages().get(stageId);
        return entry != null ? entry.getDisplayName() : stageId;
    }

    public boolean hasStage(String stage) {
        return unlockedStages.contains(stage);
    }

    public List<String> getUnlockedStages() {
        return new ArrayList<>(unlockedStages);
    }
}
