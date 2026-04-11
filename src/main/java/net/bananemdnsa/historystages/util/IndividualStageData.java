package net.bananemdnsa.historystages.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IndividualStageData extends SavedData {
    private final Map<UUID, Set<String>> playerStages = new HashMap<>();
    private static final String DATA_NAME = "historystages_individual";

    public static final Map<UUID, Set<String>> SERVER_CACHE = new ConcurrentHashMap<>();

    public IndividualStageData() {
        SERVER_CACHE.clear();
    }

    public static IndividualStageData load(CompoundTag nbt, HolderLookup.Provider registries) {
        IndividualStageData data = new IndividualStageData();
        SERVER_CACHE.clear();

        CompoundTag playersTag = nbt.getCompound("players");
        for (String uuidStr : playersTag.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            Set<String> stages = new HashSet<>();
            ListTag list = playersTag.getList(uuidStr, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                stages.add(list.getString(i));
            }

            data.playerStages.put(uuid, stages);
            SERVER_CACHE.put(uuid, ConcurrentHashMap.newKeySet());
            SERVER_CACHE.get(uuid).addAll(stages);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<UUID, Set<String>> entry : playerStages.entrySet()) {
            ListTag list = new ListTag();
            for (String stage : entry.getValue()) {
                list.add(StringTag.valueOf(stage));
            }
            playersTag.put(entry.getKey().toString(), list);
        }
        nbt.put("players", playersTag);
        return nbt;
    }

    public void refreshCache() {
        Map<UUID, Set<String>> newCache = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : playerStages.entrySet()) {
            Set<String> set = ConcurrentHashMap.newKeySet();
            set.addAll(entry.getValue());
            newCache.put(entry.getKey(), set);
        }
        SERVER_CACHE.keySet().retainAll(newCache.keySet());
        SERVER_CACHE.putAll(newCache);
    }

    public static IndividualStageData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            IndividualStageData data = serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(
                            new SavedData.Factory<>(IndividualStageData::new, IndividualStageData::load),
                            DATA_NAME
                    );
            data.refreshCache();
            return data;
        }
        return new IndividualStageData();
    }

    public void addStage(UUID player, String stage) {
        playerStages.computeIfAbsent(player, k -> new HashSet<>()).add(stage);
        SERVER_CACHE.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(stage);
        setDirty();
    }

    public boolean removeStage(UUID player, String stage) {
        Set<String> stages = playerStages.get(player);
        if (stages != null && stages.remove(stage)) {
            Set<String> cached = SERVER_CACHE.get(player);
            if (cached != null) {
                cached.remove(stage);
            }
            setDirty();
            return true;
        }
        return false;
    }

    public boolean hasStage(UUID player, String stage) {
        Set<String> stages = playerStages.get(player);
        return stages != null && stages.contains(stage);
    }

    public static boolean hasStageCached(UUID player, String stage) {
        Set<String> stages = SERVER_CACHE.get(player);
        return stages != null && stages.contains(stage);
    }

    public Set<String> getUnlockedStages(UUID player) {
        Set<String> stages = playerStages.get(player);
        return stages != null ? new HashSet<>(stages) : new HashSet<>();
    }

    public Set<UUID> getAllPlayersWithStage(String stage) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, Set<String>> entry : playerStages.entrySet()) {
            if (entry.getValue().contains(stage)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
