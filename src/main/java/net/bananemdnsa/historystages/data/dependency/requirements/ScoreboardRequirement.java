package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementContext;
import net.bananemdnsa.historystages.data.dependency.ScoreboardDep;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/** Scoreboard objectives compared against a value, either for the player or a named holder. */
public class ScoreboardRequirement implements Requirement {

    @Override
    public String id() {
        return "scoreboard";
    }

    @Override
    public List<DependencyResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<DependencyResult.EntryResult> results = new ArrayList<>();
        for (ScoreboardDep sb : group.getScoreboard()) {
            int current = getScoreboardValue(ctx.level(), ctx.player(), sb);
            boolean met = sb.compare(current);
            String holderSuffix = sb.isPlayerSelf() ? "" : " [" + sb.getScoreHolder() + "]";
            String desc = sb.getObjective() + " " + sb.getOp() + " " + sb.getValue() + holderSuffix;
            results.add(new DependencyResult.EntryResult("scoreboard", sb.getObjective(),
                    desc, met, current, sb.getValue()));
        }
        return results;
    }

    private static int getScoreboardValue(Level level, ServerPlayer player, ScoreboardDep dep) {
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
