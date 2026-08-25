package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.data.saveddata.StageData;

/** Global stages that have to be unlocked. */
public class StageRequirement implements Requirement {

    @Override
    public String id() {
        return "stage";
    }

    @Override
    public String tabLangKey() {
        return "editor.historystages.dep.tab.global_stages";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.global_stages";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.stage_deps";
    }

    @Override
    public boolean declaredIn(DependencyGroup group) {
        return !group.getStages().isEmpty();
    }

    @Override
    public List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<RequirementResult.EntryResult> results = new ArrayList<>();
        for (String stageId : group.getStages()) {
            boolean met = false;
            if (ctx.level() != null && !ctx.level().isClientSide()) {
                StageData data = StageData.get(ctx.level());
                met = data.getUnlockedStages().contains(stageId);
            }
            StageEntry stageEntry = StageManager.getStages().get(stageId);
            String name = stageEntry != null ? stageEntry.getDisplayName() : stageId;
            results.add(new RequirementResult.EntryResult("stage", stageId, name, met, met ? 1 : 0, 1));
        }
        return results;
    }
}
