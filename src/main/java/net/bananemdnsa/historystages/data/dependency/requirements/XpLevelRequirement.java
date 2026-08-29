package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.api.dependency.RequirementDisplay;
import net.bananemdnsa.historystages.data.dependency.DependencyProgress;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.bananemdnsa.historystages.api.stage.StageScope;

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
    public String tabLangKey() {
        return "editor.historystages.dep.tab.xp_level";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.xp_level";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.xp";
    }

    @Override
    public Set<StageScope> supportedScopes() {
        return Set.of(StageScope.INDIVIDUAL);
    }

    @Override
    public RequirementDisplay.Kind displayKind() {
        return RequirementDisplay.Kind.COUNTED;
    }

    @Override
    public boolean declaredIn(DependencyGroup group) {
        return group.getXpLevel() != null && group.getXpLevel().getLevel() > 0;
    }

    @Override
    public List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<RequirementResult.EntryResult> results = new ArrayList<>();
        XpLevelDep xpLevel = group.getXpLevel();
        if (xpLevel != null && xpLevel.getLevel() > 0) {
            boolean met;
            int currentLevel = ctx.player() != null ? ctx.player().experienceLevel : 0;
            if (xpLevel.isConsume()) {
                met = ctx.depositedData() != null
                        && ctx.depositedData().getBoolean(ctx.progressKey(DependencyProgress.XP_SUFFIX));
                currentLevel = met ? xpLevel.getLevel() : currentLevel;
            } else {
                met = currentLevel >= xpLevel.getLevel();
            }
            boolean needsDeposit = xpLevel.isConsume() && !met;
            String desc = "Level " + xpLevel.getLevel() + (xpLevel.isConsume() ? " (consumed)" : "");
            results.add(new RequirementResult.EntryResult("xp_level", "xp", desc, met,
                    currentLevel, xpLevel.getLevel(), needsDeposit));
        }
        return results;
    }
}
