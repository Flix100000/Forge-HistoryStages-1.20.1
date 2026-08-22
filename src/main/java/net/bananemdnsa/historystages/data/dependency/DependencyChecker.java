package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.*;

public class DependencyChecker {

    /**
     * Check all dependency groups for a stage. Groups are AND-connected.
     *
     * @param entry         The stage entry
     * @param player        The player (null for global-only checks)
     * @param level         The server level
     * @param scope         Which stage map this entry came from. No default is offered: guessing
     *                      wrong silently changes which requirement kinds are evaluated at all.
     * @param depositedData The tracking NBT from the scroll, if applicable
     * @return DependencyResult with per-group and per-entry details
     */
    public static DependencyResult checkAll(StageEntry entry, ServerPlayer player, Level level,
            StageScope scope, CompoundTag depositedData) {
        return checkAll(entry, player, level, scope, depositedData, 0.0);
    }

    public static DependencyResult checkAll(StageEntry entry, ServerPlayer player, Level level,
            StageScope scope, CompoundTag depositedData, double costReduction) {
        List<DependencyGroup> groups = entry.getDependencies();
        if (groups == null || groups.isEmpty()) {
            return DependencyResult.noDependencies();
        }

        List<DependencyResult.GroupResult> groupResults = new ArrayList<>();
        boolean allFulfilled = true;

        for (int i = 0; i < groups.size(); i++) {
            DependencyResult.GroupResult result = checkGroup(groups.get(i), i, player, level,
                    scope, depositedData, costReduction);
            groupResults.add(result);
            if (!result.isFulfilled()) {
                allFulfilled = false;
            }
        }

        return new DependencyResult(allFulfilled, groupResults);
    }

    public static DependencyResult.GroupResult checkGroup(DependencyGroup group, int groupIndex,
            ServerPlayer player, Level level, StageScope scope, CompoundTag depositedData) {
        return checkGroup(group, groupIndex, player, level, scope, depositedData, 0.0);
    }

    /**
     * Check a single dependency group. Entries within are connected by the group's
     * logic (AND/OR). costReduction in [0,0.9] shrinks item requirements.
     *
     * <p>Only requirements the scope can answer are asked. A kill or an advancement demanded of a
     * global stage has no answer — there is no single player to measure it against — so it is
     * skipped rather than checked against whoever happened to trigger this.
     */
    public static DependencyResult.GroupResult checkGroup(DependencyGroup group, int groupIndex,
            ServerPlayer player, Level level, StageScope scope, CompoundTag depositedData,
            double costReduction) {
        List<DependencyResult.EntryResult> entries = new ArrayList<>();
        boolean isActuallyOr = "OR".equalsIgnoreCase(group.getLogic());

        RequirementContext ctx = new RequirementContext(player, level, depositedData, groupIndex,
                costReduction, scope);
        for (Requirement requirement : RequirementTypes.forScope(scope)) {
            entries.addAll(requirement.evaluate(group, ctx));
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

    // --- Consume methods removed: item/XP consumption is now handled via
    //     DepositDependencyPacket when the player explicitly deposits resources. ---
}
