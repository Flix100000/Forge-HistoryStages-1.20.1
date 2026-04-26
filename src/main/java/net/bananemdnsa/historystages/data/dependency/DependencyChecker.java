package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.*;

public class DependencyChecker {

    /**
     * Check all dependency groups for a stage. Groups are AND-connected.
     *
     * @param entry         The stage entry
     * @param player        The player (null for global-only checks)
     * @param level         The server level
     * @param depositedData The tracking NBT from the scroll, if applicable
     * @return DependencyResult with per-group and per-entry details
     */
    public static DependencyResult checkAll(StageEntry entry, ServerPlayer player, Level level,
            CompoundTag depositedData) {
        List<DependencyGroup> groups = entry.getDependencies();
        if (groups == null || groups.isEmpty()) {
            return DependencyResult.noDependencies();
        }

        List<DependencyResult.GroupResult> groupResults = new ArrayList<>();
        boolean allFulfilled = true;

        for (int i = 0; i < groups.size(); i++) {
            DependencyResult.GroupResult result = checkGroup(groups.get(i), i, player, level, depositedData);
            groupResults.add(result);
            if (!result.isFulfilled()) {
                allFulfilled = false;
            }
        }

        return new DependencyResult(allFulfilled, groupResults);
    }

    /**
     * Check a single dependency group. Entries within are connected by the group's
     * logic (AND/OR).
     */
    public static DependencyResult.GroupResult checkGroup(DependencyGroup group, int groupIndex,
            ServerPlayer player, Level level, CompoundTag depositedData) {
        List<DependencyResult.EntryResult> entries = new ArrayList<>();
        boolean isActuallyOr = "OR".equalsIgnoreCase(group.getLogic());

        // Items — tracked via deposited NBT, not live inventory
        for (DependencyItem item : group.getItems()) {
            int current = (depositedData != null)
                    ? depositedData.getInt("Group_" + groupIndex + "_Item_" + item.getId())
                    : 0;
            boolean met = current >= item.getCount();
            String itemName = getItemDisplayName(item.getId());
            entries.add(new DependencyResult.EntryResult("item", item.getId(),
                    item.getCount() + "x " + itemName, met, current, item.getCount()));
        }

        // Global Stages
        for (String stageId : group.getStages()) {
            boolean met = false;
            if (level != null && !level.isClientSide()) {
                StageData data = StageData.get(level);
                met = data.getUnlockedStages().contains(stageId);
            }
            StageEntry stageEntry = StageManager.getStages().get(stageId);
            String name = stageEntry != null ? stageEntry.getDisplayName() : stageId;
            entries.add(new DependencyResult.EntryResult("stage", stageId, name, met, met ? 1 : 0, 1));
        }

        // Individual Stages (all online / all ever)
        for (IndividualStageDep dep : group.getIndividualStages()) {
            boolean met = checkIndividualStageDep(dep, level);
            StageEntry stageEntry = StageManager.getIndividualStages().get(dep.getStageId());
            String name = stageEntry != null ? stageEntry.getDisplayName() : dep.getStageId();
            String modeLabel = dep.isAllEver() ? " (all ever)" : " (all online)";
            entries.add(new DependencyResult.EntryResult("individual_stage", dep.getStageId(),
                    name + modeLabel, met, met ? 1 : 0, 1));
        }

        // Advancements (individual player only)
        for (String advId : group.getAdvancements()) {
            boolean met = player != null && checkAdvancement(player, advId);
            entries.add(new DependencyResult.EntryResult("advancement", advId, advId, met, met ? 1 : 0, 1));
        }

        // XP Level
        XpLevelDep xpLevel = group.getXpLevel();
        if (xpLevel != null && xpLevel.getLevel() > 0) {
            boolean met;
            int currentLevel = player != null ? player.experienceLevel : 0;
            if (xpLevel.isConsume()) {
                met = depositedData != null && depositedData.getBoolean("Group_" + groupIndex + "_XP");
                currentLevel = met ? xpLevel.getLevel() : currentLevel;
            } else {
                met = currentLevel >= xpLevel.getLevel();
            }
            boolean needsDeposit = xpLevel.isConsume() && !met;
            String desc = "Level " + xpLevel.getLevel() + (xpLevel.isConsume() ? " (consumed)" : "");
            entries.add(new DependencyResult.EntryResult("xp_level", "xp", desc, met,
                    currentLevel, xpLevel.getLevel(), needsDeposit));
        }

        // Entity Kills
        for (EntityKillDep kill : group.getEntityKills()) {
            int current = player != null ? getKillCount(player, kill.getEntityId()) : 0;
            boolean met = current >= kill.getCount();
            String entityName = getEntityDisplayName(kill.getEntityId());
            entries.add(new DependencyResult.EntryResult("entity_kill", kill.getEntityId(),
                    kill.getCount() + "x " + entityName, met, current, kill.getCount()));
        }

        // Stats
        for (StatDep stat : group.getStats()) {
            int current = player != null ? getStatValue(player, stat.getStatId()) : 0;
            boolean met = current >= stat.getMinValue();
            entries.add(new DependencyResult.EntryResult("stat", stat.getStatId(),
                    stat.getStatId() + " >= " + stat.getMinValue(), met, current, stat.getMinValue()));
        }

        // Determine group fulfillment based on logic (Default: AND)
        boolean fulfilled;
        if (entries.isEmpty()) {
            fulfilled = true;
        } else if (isActuallyOr) {
            fulfilled = entries.stream().anyMatch(DependencyResult.EntryResult::isFulfilled);
        } else {
            // Must be AND
            fulfilled = entries.stream().allMatch(DependencyResult.EntryResult::isFulfilled);
        }

        return new DependencyResult.GroupResult(group.getLogic(), fulfilled, entries);
    }

    // --- Helper Methods ---

    private static boolean checkIndividualStageDep(IndividualStageDep dep, Level level) {
        if (level == null || level.isClientSide() || level.getServer() == null) return false;

        IndividualStageData data = IndividualStageData.get(level);

        if (dep.isAllEver()) {
            // Every player who ever had any stage must have this stage
            Set<UUID> allPlayers = getAllKnownPlayers();
            if (allPlayers.isEmpty()) return false;
            for (UUID uuid : allPlayers) {
                if (!data.hasStage(uuid, dep.getStageId())) {
                    return false;
                }
            }
            return true;
        } else {
            // All currently online players must have this stage
            var players = level.getServer().getPlayerList().getPlayers();
            if (players.isEmpty()) return false;
            for (var player : players) {
                if (!data.hasStage(player.getUUID(), dep.getStageId())) {
                    return false;
                }
            }
            return true;
        }
    }

    private static Set<UUID> getAllKnownPlayers() {
        return IndividualStageData.SERVER_CACHE.keySet();
    }

    private static boolean checkAdvancement(ServerPlayer player, String advancementId) {
        ResourceLocation rl = ResourceLocation.tryParse(advancementId);
        if (rl == null || player.getServer() == null) return false;
        AdvancementHolder holder = player.getServer().getAdvancements().get(rl);
        if (holder == null) return false;
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static int getKillCount(ServerPlayer player, String entityId) {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null) return 0;
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(rl);
        if (entityType == null) return 0;
        ServerStatsCounter stats = player.getStats();
        return stats.getValue(Stats.ENTITY_KILLED.get(entityType));
    }

    private static int getStatValue(ServerPlayer player, String statId) {
        ResourceLocation rl = ResourceLocation.tryParse(statId);
        if (rl == null) return 0;
        try {
            return player.getStats().getValue(Stats.CUSTOM.get(rl));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String getItemDisplayName(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return itemId;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null) return itemId;
        return item.getDescription().getString();
    }

    private static String getEntityDisplayName(String entityId) {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null) return entityId;
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(rl);
        if (entityType == null) return entityId;
        return entityType.getDescription().getString();
    }

    // --- Consume methods removed: item/XP consumption is now handled via
    //     DepositDependencyPacket when the player explicitly deposits resources. ---
}

