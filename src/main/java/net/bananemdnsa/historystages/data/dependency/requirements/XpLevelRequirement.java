package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementContext;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;

/**
 * An experience level the player has to reach, optionally consumed on deposit.
 *
 * <p>The only requirement backed by a single object rather than a list, so it contributes at
 * most one entry. That needs no special case — a view is free to return however many it has.
 */
public class XpLevelRequirement implements Requirement {

    @Override
    public String id() {
        return "xp_level";
    }

    @Override
    public List<DependencyResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<DependencyResult.EntryResult> results = new ArrayList<>();
        XpLevelDep xpLevel = group.getXpLevel();
        if (xpLevel != null && xpLevel.getLevel() > 0) {
            boolean met;
            int currentLevel = ctx.player() != null ? ctx.player().experienceLevel : 0;
            if (xpLevel.isConsume()) {
                met = ctx.depositedData() != null && ctx.depositedData().getBoolean("Group_" + ctx.groupIndex() + "_XP");
                currentLevel = met ? xpLevel.getLevel() : currentLevel;
            } else {
                met = currentLevel >= xpLevel.getLevel();
            }
            boolean needsDeposit = xpLevel.isConsume() && !met;
            String desc = "Level " + xpLevel.getLevel() + (xpLevel.isConsume() ? " (consumed)" : "");
            results.add(new DependencyResult.EntryResult("xp_level", "xp", desc, met,
                    currentLevel, xpLevel.getLevel(), needsDeposit));
        }
        return results;
    }
}
