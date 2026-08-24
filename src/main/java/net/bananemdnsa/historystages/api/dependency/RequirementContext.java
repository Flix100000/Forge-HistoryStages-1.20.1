package net.bananemdnsa.historystages.api.dependency;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Everything the requirement views need to evaluate a group, assembled once per group.
 *
 * <p>Carries exactly what the views read and nothing more. A view that turns out to need
 * something else widens this record — deliberately, and visibly to every other view.
 *
 * @param player        the player, or null for global-only checks
 * @param level         the server level, or null when there is none
 * @param depositedData the tracking NBT from the scroll, if applicable
 * @param groupIndex    this group's position in the stage, which item progress is keyed by
 * @param costReduction booster reduction in [0,0.9], shrinking item requirements
 * @param scope         whether this check is about a global stage or an individual one; decides
 *                      which requirement kinds are asked at all
 */
public record RequirementContext(ServerPlayer player, Level level, CompoundTag depositedData,
        int groupIndex, double costReduction, StageScope scope) {
}
