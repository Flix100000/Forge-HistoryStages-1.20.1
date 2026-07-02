package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;

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
        return checkAll(entry, player, level, depositedData, 0.0);
    }

    public static DependencyResult checkAll(StageEntry entry, ServerPlayer player, Level level,
            CompoundTag depositedData, double costReduction) {
        List<DependencyGroup> groups = entry.getDependencies();
        if (groups == null || groups.isEmpty()) {
            return DependencyResult.noDependencies();
        }

        List<DependencyResult.GroupResult> groupResults = new ArrayList<>();
        boolean allFulfilled = true;

        for (int i = 0; i < groups.size(); i++) {
            DependencyResult.GroupResult result = checkGroup(groups.get(i), i, player, level, depositedData,
                    costReduction);
            groupResults.add(result);
            if (!result.isFulfilled()) {
                allFulfilled = false;
            }
        }

        return new DependencyResult(allFulfilled, groupResults);
    }

    public static DependencyResult.GroupResult checkGroup(DependencyGroup group, int groupIndex, ServerPlayer player,
            Level level, CompoundTag depositedData) {
        return checkGroup(group, groupIndex, player, level, depositedData, 0.0);
    }

    /**
     * Check a single dependency group. Entries within are connected by the group's
     * logic (AND/OR). costReduction in [0,0.9] shrinks item requirements.
     */
    public static DependencyResult.GroupResult checkGroup(DependencyGroup group, int groupIndex, ServerPlayer player,
            Level level, CompoundTag depositedData, double costReduction) {
        List<DependencyResult.EntryResult> entries = new ArrayList<>();
        boolean isActuallyOr = "OR".equalsIgnoreCase(group.getLogic());

        // Items
        for (DependencyItem item : group.getItems()) {
            ResourceLocation rl = ResourceLocation.tryParse(item.getId());
            String idString = rl != null ? rl.toString() : item.getId();

            int original = item.getCount();
            int required = BoosterUtil.effectiveCount(original, costReduction);
            int current = (depositedData != null)
                    ? depositedData.getInt("Group_" + groupIndex + "_Item_" + idString)
                    : 0;
            boolean met = current >= required;
            String itemName = getItemDisplayName(item.getId());
            int originalForUi = (required == original) ? 0 : original;
            entries.add(
                    new DependencyResult.EntryResult("item", idString, required + "x " + itemName, met, current,
                            required, originalForUi, false));
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
            entries.add(new DependencyResult.EntryResult("stage", name, met));
        }

        // Individual Stages (all online / all ever)
        for (IndividualStageDep dep : group.getIndividualStages()) {
            boolean met = checkIndividualStageDep(dep, level);
            StageEntry stageEntry = StageManager.getIndividualStages().get(dep.getStageId());
            String name = stageEntry != null ? stageEntry.getDisplayName() : dep.getStageId();
            String modeLabel = dep.isAllEver() ? " (all ever)" : " (all online)";
            entries.add(new DependencyResult.EntryResult("individual_stage", name + modeLabel, met));
        }

        // Advancements (individual stages only)
        for (String advId : group.getAdvancements()) {
            boolean met = player != null && checkAdvancement(player, advId);
            entries.add(new DependencyResult.EntryResult("advancement", advId, met));
        }

        // XP Level
        XpLevelDep xpLevel = group.getXpLevel();
        if (xpLevel != null && xpLevel.getLevel() > 0) {
            boolean met = false;
            int currentLevel = player != null ? player.experienceLevel : 0;
            if (xpLevel.isConsume()) {
                met = depositedData != null && depositedData.getBoolean("Group_" + groupIndex + "_XP");
                currentLevel = met ? xpLevel.getLevel() : currentLevel; // Show maxed if consumed
            } else {
                met = currentLevel >= xpLevel.getLevel();
            }
            boolean needsDeposit = xpLevel.isConsume() && !met;
            String desc = "Level " + xpLevel.getLevel() + (xpLevel.isConsume() ? " (consumed)" : "");
            entries.add(
                    new DependencyResult.EntryResult("xp_level", "xp", desc, met, currentLevel, xpLevel.getLevel(),
                            needsDeposit));
        }

        // Entity Kills
        for (EntityKillDep kill : group.getEntityKills()) {
            int current = player != null ? getKillCount(player, kill.getEntityId()) : 0;
            boolean met = current >= kill.getCount();
            String entityName = getEntityDisplayName(kill.getEntityId());
            entries.add(new DependencyResult.EntryResult("entity_kill", kill.getEntityId(),
                    kill.getCount() + "x " + entityName, met,
                    current, kill.getCount()));
        }

        // Stats
        for (StatDep stat : group.getStats()) {
            int current = player != null ? getStatValue(player, stat.getStatId()) : 0;
            boolean met = current >= stat.getMinValue();
            entries.add(new DependencyResult.EntryResult("stat", stat.getStatId(),
                    stat.getStatId() + " >= " + stat.getMinValue(), met,
                    current, stat.getMinValue()));
        }

        // Scoreboard
        for (ScoreboardDep sb : group.getScoreboard()) {
            int current = getScoreboardValue(level, player, sb);
            boolean met = sb.compare(current);
            String holderSuffix = sb.isPlayerSelf() ? "" : " [" + sb.getScoreHolder() + "]";
            String desc = sb.getObjective() + " " + sb.getOp() + " " + sb.getValue() + holderSuffix;
            entries.add(new DependencyResult.EntryResult("scoreboard", sb.getObjective(),
                    desc, met, current, sb.getValue()));
        }

        // Determine group fulfillment based on logic (Default to AND)
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

    private static int countItemInInventory(ServerPlayer player, String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null)
            return 0;
        var item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null)
            return 0;

        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ForgeRegistries.ITEMS.getKey(stack.getItem()) != null
                    && ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(rl)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean checkIndividualStageDep(IndividualStageDep dep, Level level) {
        if (level == null || level.isClientSide() || level.getServer() == null)
            return false;

        IndividualStageData data = IndividualStageData.get(level);

        if (dep.isAllEver()) {
            // Check all players who ever joined (stored in IndividualStageData)
            // Every player in the data must have this stage
            Set<UUID> allPlayers = getAllKnownPlayers(data);
            if (allPlayers.isEmpty())
                return false;
            for (UUID uuid : allPlayers) {
                if (!data.hasStage(uuid, dep.getStageId())) {
                    return false;
                }
            }
            return true;
        } else {
            // Check all currently online players
            var players = level.getServer().getPlayerList().getPlayers();
            if (players.isEmpty())
                return false;
            for (var player : players) {
                if (!data.hasStage(player.getUUID(), dep.getStageId())) {
                    return false;
                }
            }
            return true;
        }
    }

    private static Set<UUID> getAllKnownPlayers(IndividualStageData data) {
        // Use the server cache which contains all players who ever had any stage
        return IndividualStageData.SERVER_CACHE.keySet();
    }

    private static boolean checkAdvancement(ServerPlayer player, String advancementId) {
        ResourceLocation rl = ResourceLocation.tryParse(advancementId);
        if (rl == null)
            return false;
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(rl);
        if (advancement == null)
            return false;
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static int getKillCount(ServerPlayer player, String entityId) {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null)
            return 0;
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (entityType == null)
            return 0;
        ServerStatsCounter stats = player.getStats();
        return stats.getValue(Stats.ENTITY_KILLED.get(entityType));
    }

    private static int getStatValue(ServerPlayer player, String statId) {
        ResourceLocation rl = ResourceLocation.tryParse(statId);
        if (rl == null)
            return 0;
        try {
            return player.getStats().getValue(Stats.CUSTOM.get(rl));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getScoreboardValue(Level level, ServerPlayer player, ScoreboardDep dep) {
        if (level == null || dep.getObjective() == null || dep.getObjective().isEmpty()) return 0;
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getObjective(dep.getObjective());
        if (objective == null) return 0;
        String holderName = dep.isPlayerSelf()
                ? (player != null ? player.getScoreboardName() : null)
                : dep.getScoreHolder();
        if (holderName == null) return 0;
        if (!scoreboard.hasPlayerScore(holderName, objective)) return 0;
        return scoreboard.getOrCreatePlayerScore(holderName, objective).getScore();
    }

    private static String getItemDisplayName(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null)
            return itemId;
        var item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null)
            return itemId;
        return item.getDescription().getString();
    }

    private static String getEntityDisplayName(String entityId) {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null)
            return entityId;
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (entityType == null)
            return entityId;
        return entityType.getDescription().getString();
    }

    // --- Consume Methods removed, handled via packet on deposit ---
}
