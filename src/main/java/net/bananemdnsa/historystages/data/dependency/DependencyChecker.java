package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DependencyChecker {
    private DependencyChecker() {
    }

    public static DependencyResult checkAll(StageEntry entry, ServerPlayer player, Level level, CompoundTag depositedData) {
        List<DependencyGroup> groups = entry.getDependencies();
        if (groups == null || groups.isEmpty()) {
            return DependencyResult.noDependencies();
        }

        List<DependencyResult.GroupResult> results = new ArrayList<>();
        boolean fulfilled = true;
        for (int i = 0; i < groups.size(); i++) {
            DependencyResult.GroupResult result = checkGroup(groups.get(i), i, player, level, depositedData);
            results.add(result);
            if (!result.isFulfilled()) {
                fulfilled = false;
            }
        }
        return new DependencyResult(fulfilled, results);
    }

    public static DependencyResult.GroupResult checkGroup(DependencyGroup group, int groupIndex, ServerPlayer player,
                                                          Level level, CompoundTag depositedData) {
        List<DependencyResult.EntryResult> entries = new ArrayList<>();
        boolean orLogic = "OR".equalsIgnoreCase(group.getLogic());

        for (DependencyItem item : group.getItems()) {
            ResourceLocation id = ResourceLocation.tryParse(item.getId());
            String idString = id != null ? id.toString() : item.getId();
            String key = "Group_" + groupIndex + "_Item_" + idString;
            int current = depositedData != null ? depositedData.getInt(key) : 0;
            boolean met = current >= item.getCount();
            entries.add(new DependencyResult.EntryResult("item", idString,
                    item.getCount() + "x " + getItemDisplayName(idString), met, current, item.getCount()));
        }

        for (String stageId : group.getStages()) {
            boolean met = level != null && !level.isClientSide() && StageData.get(level).hasStage(stageId);
            StageEntry stage = StageManager.getStages().get(stageId);
            String name = stage != null ? stage.getDisplayName() : stageId;
            entries.add(new DependencyResult.EntryResult("stage", stageId, name, met, met ? 1 : 0, 1));
        }

        for (IndividualStageDep dep : group.getIndividualStages()) {
            boolean met = checkIndividualStageDep(dep, level);
            StageEntry stage = StageManager.getIndividualStages().get(dep.getStageId());
            String name = stage != null ? stage.getDisplayName() : dep.getStageId();
            String mode = dep.isAllEver() ? " (all ever)" : " (all online)";
            entries.add(new DependencyResult.EntryResult("individual_stage", dep.getStageId(), name + mode, met,
                    met ? 1 : 0, 1));
        }

        for (String advancementId : group.getAdvancements()) {
            boolean met = player != null && checkAdvancement(player, advancementId);
            entries.add(new DependencyResult.EntryResult("advancement", advancementId, advancementId, met,
                    met ? 1 : 0, 1));
        }

        XpLevelDep xpLevel = group.getXpLevel();
        if (xpLevel != null && xpLevel.getLevel() > 0) {
            boolean met;
            int current = player != null ? player.experienceLevel : 0;
            if (xpLevel.isConsume()) {
                met = depositedData != null && depositedData.getBoolean("Group_" + groupIndex + "_XP");
                current = met ? xpLevel.getLevel() : current;
            } else {
                met = current >= xpLevel.getLevel();
            }
            boolean canDeposit = xpLevel.isConsume() && !met;
            String desc = "Level " + xpLevel.getLevel() + (xpLevel.isConsume() ? " (consumed)" : "");
            entries.add(new DependencyResult.EntryResult("xp_level", "xp", desc, met, current,
                    xpLevel.getLevel(), canDeposit));
        }

        for (EntityKillDep kill : group.getEntityKills()) {
            int current = player != null ? getKillCount(player, kill.getEntityId()) : 0;
            boolean met = current >= kill.getCount();
            entries.add(new DependencyResult.EntryResult("entity_kill", kill.getEntityId(),
                    kill.getCount() + "x " + getEntityDisplayName(kill.getEntityId()), met, current, kill.getCount()));
        }

        for (StatDep stat : group.getStats()) {
            int current = player != null ? getStatValue(player, stat.getStatId()) : 0;
            boolean met = current >= stat.getMinValue();
            entries.add(new DependencyResult.EntryResult("stat", stat.getStatId(),
                    stat.getStatId() + " >= " + stat.getMinValue(), met, current, stat.getMinValue()));
        }

        boolean fulfilled;
        if (entries.isEmpty()) {
            fulfilled = true;
        } else if (orLogic) {
            fulfilled = entries.stream().anyMatch(DependencyResult.EntryResult::isFulfilled);
        } else {
            fulfilled = entries.stream().allMatch(DependencyResult.EntryResult::isFulfilled);
        }

        return new DependencyResult.GroupResult(group.getLogic(), fulfilled, entries);
    }

    private static boolean checkIndividualStageDep(IndividualStageDep dep, Level level) {
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return false;
        }

        IndividualStageData data = IndividualStageData.get(level);
        if (dep.isAllEver()) {
            Set<UUID> allPlayers = IndividualStageData.SERVER_CACHE.keySet();
            if (allPlayers.isEmpty()) {
                return false;
            }
            for (UUID uuid : allPlayers) {
                if (!data.hasStage(uuid, dep.getStageId())) {
                    return false;
                }
            }
            return true;
        }

        var players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return false;
        }
        for (ServerPlayer player : players) {
            if (!data.hasStage(player.getUUID(), dep.getStageId())) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkAdvancement(ServerPlayer player, String advancementId) {
        ResourceLocation id = ResourceLocation.tryParse(advancementId);
        if (id == null) {
            return false;
        }
        AdvancementHolder advancement = player.server.getAdvancements().get(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static int getKillCount(ServerPlayer player, String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) {
            return 0;
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (entityType == null) {
            return 0;
        }
        ServerStatsCounter stats = player.getStats();
        return stats.getValue(Stats.ENTITY_KILLED.get(entityType));
    }

    private static int getStatValue(ServerPlayer player, String statId) {
        ResourceLocation id = ResourceLocation.tryParse(statId);
        if (id == null) {
            return 0;
        }
        try {
            return player.getStats().getValue(Stats.CUSTOM.get(id));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String getItemDisplayName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return itemId;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null || item == Items.AIR ? itemId : item.getDescription().getString();
    }

    private static String getEntityDisplayName(String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) {
            return entityId;
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(id);
        return entityType != null ? entityType.getDescription().getString() : entityId;
    }
}
