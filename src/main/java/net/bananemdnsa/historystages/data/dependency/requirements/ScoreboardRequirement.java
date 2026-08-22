package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementContext;
import net.bananemdnsa.historystages.data.dependency.ScoreboardDep;

/** Scoreboard objectives compared against a value, either for the player or a named holder. */
public class ScoreboardRequirement implements Requirement {

    @Override
    public String id() {
        return "scoreboard";
    }

    @Override
    public String tabLangKey() {
        return "editor.historystages.dep.tab.scoreboard";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.scoreboard";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.scoreboard";
    }

    @Override
    public boolean declaredIn(DependencyGroup group) {
        return !group.getScoreboard().isEmpty();
    }

    @Override
    public List<DependencyResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<DependencyResult.EntryResult> results = new ArrayList<>();
        for (ScoreboardDep sb : group.getScoreboard()) {
            int current = ScoreboardLookup.valueOf(ctx.level(), ctx.player(), sb);
            boolean met = sb.compare(current);
            String holderSuffix = sb.isPlayerSelf() ? "" : " [" + sb.getScoreHolder() + "]";
            String desc = sb.getObjective() + " " + sb.getOp() + " " + sb.getValue() + holderSuffix;
            results.add(new DependencyResult.EntryResult("scoreboard", sb.getObjective(),
                    desc, met, current, sb.getValue()));
        }
        return results;
    }

}
