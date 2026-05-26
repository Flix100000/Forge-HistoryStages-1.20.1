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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IndividualStageData extends SavedData {
    private final Map<UUID, Set<String>> playerStages = new HashMap<>();
    private static final String DATA_NAME = "historystages_individual";

    public static final Map<UUID, Set<String>> SERVER_CACHE = new ConcurrentHashMap<>();

    public IndividualStageData() {
        SERVER_CACHE.clear();
    }

    public static IndividualStageData load(CompoundTag nbt, HolderLookup.Provider provider) {
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
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
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
        // Remove players no longer in data
        SERVER_CACHE.keySet().retainAll(newCache.keySet());
        // Add/update all players
        SERVER_CACHE.putAll(newCache);
    }

    public static IndividualStageData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            IndividualStageData data = serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(new Factory<>(IndividualStageData::new, IndividualStageData::load, DataFixTypes.LEVEL), DATA_NAME);
            data.refreshCache();
            return data;
        }
        return new IndividualStageData();
    }

    public void addStage(UUID player, String stage) {
        boolean added = playerStages.computeIfAbsent(player, k -> new HashSet<>()).add(stage);
        SERVER_CACHE.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(stage);
        if (added) {
            OptionalFTBQuestsHooks.individualUnlocked(stage, player);
            setDirty();
            String displayName = displayNameOf(stage);
            net.bananemdnsa.historystages.api.StageEvents.INDIVIDUAL_UNLOCKED.invoker().onChanged(stage, displayName, player);

            net.minecraft.server.MinecraftServer server = ServerHolder.get();
            if (server != null) {
                net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayer(player);
                if (sp != null) {
                    var entry = net.bananemdnsa.historystages.data.StageManager.getIndividualStages().get(stage);
                    String icon = entry != null ? entry.getIcon() : null;
                    net.bananemdnsa.historystages.network.Networking.sendStageUnlockedToast(sp, displayName, icon);
                }
            }
        }
    }

    private static String displayNameOf(String stageId) {
        var entry = net.bananemdnsa.historystages.data.StageManager.getIndividualStages().get(stageId);
        return entry != null ? entry.getDisplayName() : stageId;
    }

    public boolean removeStage(UUID player, String stage) {
        Set<String> stages = playerStages.get(player);
        if (stages != null && stages.remove(stage)) {
            Set<String> cached = SERVER_CACHE.get(player);
            if (cached != null) {
                cached.remove(stage);
            }
            setDirty();
            net.bananemdnsa.historystages.api.StageEvents.INDIVIDUAL_LOCKED.invoker().onChanged(stage, displayNameOf(stage), player);
            return true;
        }
        return false;
    }

    /**
     * Removes the stage and, if {@code individualDropOnRevoke} is enabled, drops every
     * inventory item that would now be locked.
     */
    public boolean removeStageWithDrop(net.minecraft.server.MinecraftServer server, UUID player, String stage) {
        boolean removed = removeStage(player, stage);
        if (removed && net.bananemdnsa.historystages.Config.COMMON.individualDropOnRevoke && server != null) {
            net.minecraft.server.level.ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
            if (serverPlayer != null) {
                StageLockHelper.dropLockedItemsForPlayer(serverPlayer, stage);
            }
        }
        return removed;
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
