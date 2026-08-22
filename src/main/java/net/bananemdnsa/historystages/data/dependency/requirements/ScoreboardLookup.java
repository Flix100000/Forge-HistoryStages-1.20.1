package net.bananemdnsa.historystages.data.dependency.requirements;

import net.bananemdnsa.historystages.data.dependency.ScoreboardDep;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/**
 * Reads one scoreboard value. Split out of {@link ScoreboardRequirement} so that class can be
 * loaded without Minecraft.
 *
 * <p>Not a style choice. Assigning a {@code ServerPlayer} to a {@code ScoreHolder} makes the
 * bytecode verifier load both classes when the enclosing class is first touched, which took the
 * whole built-in list down with it the moment a unit test asked a requirement for its metadata.
 * Everything that needs a Minecraft type to be <em>assignable to another</em> Minecraft type
 * belongs behind a call like this one.
 */
final class ScoreboardLookup {

    private ScoreboardLookup() {}

    static int valueOf(Level level, ServerPlayer player, ScoreboardDep dep) {
        if (level == null || dep.getObjective() == null || dep.getObjective().isEmpty()) return 0;
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getObjective(dep.getObjective());
        if (objective == null) return 0;
        ScoreHolder holder;
        if (dep.isPlayerSelf()) {
            if (player == null) return 0;
            holder = player;
        } else {
            holder = ScoreHolder.forNameOnly(dep.getScoreHolder());
        }
        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, objective);
        return info != null ? info.value() : 0;
    }
}
