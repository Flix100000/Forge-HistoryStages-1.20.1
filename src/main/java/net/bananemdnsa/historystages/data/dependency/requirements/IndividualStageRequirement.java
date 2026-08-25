package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.minecraft.world.level.Level;

/** Individual stages, demanded of either everyone online or everyone ever seen. */
public class IndividualStageRequirement implements Requirement {

    @Override
    public String id() {
        return "individual_stage";
    }

    @Override
    public String tabLangKey() {
        return "editor.historystages.dep.tab.individual_stages";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.individual_stages";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.stage_deps";
    }

    @Override
    public boolean declaredIn(DependencyGroup group) {
        return !group.getIndividualStages().isEmpty();
    }

    @Override
    public List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<RequirementResult.EntryResult> results = new ArrayList<>();
        for (IndividualStageDep dep : group.getIndividualStages()) {
            boolean met = checkIndividualStageDep(dep, ctx.level());
            StageEntry stageEntry = StageManager.getIndividualStages().get(dep.getStageId());
            String name = stageEntry != null ? stageEntry.getDisplayName() : dep.getStageId();
            String modeLabel = dep.isAllEver() ? " (all ever)" : " (all online)";
            results.add(new RequirementResult.EntryResult("individual_stage", dep.getStageId(),
                    name + modeLabel, met, met ? 1 : 0, 1));
        }
        return results;
    }

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
}
