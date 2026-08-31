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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Individual stages, demanded of everyone online, everyone ever seen, or the researcher alone. */
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
            boolean met = checkIndividualStageDep(dep, ctx);
            StageEntry stageEntry = StageManager.getIndividualStages().get(dep.getStageId());
            String name = stageEntry != null ? stageEntry.getDisplayName() : dep.getStageId();
            String modeLabel = modeLabel(dep);
            results.add(new RequirementResult.EntryResult("individual_stage", dep.getStageId(),
                    name + modeLabel, met, met ? 1 : 0, 1));
        }
        return results;
    }

    private static boolean checkIndividualStageDep(IndividualStageDep dep, RequirementContext ctx) {
        Level level = ctx.level();
        if (level == null || level.isClientSide() || level.getServer() == null) return false;

        IndividualStageData data = IndividualStageData.get(level);

        if (dep.isPlayer()) {
            // Only whoever is researching. Unmet without one rather than skipped: a check with no
            // player cannot answer the question, and answering "yes" would hand out the stage.
            ServerPlayer player = ctx.player();
            return player != null && data.hasStage(player.getUUID(), dep.getStageId());
        }

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

    /**
     * The suffix that says whose stage was asked for. Hardcoded English, like the two it joins:
     * this text is built on the server and shipped to the client as-is, so a translation here
     * would be the server's language and not the reader's.
     */
    private static String modeLabel(IndividualStageDep dep) {
        if (dep.isPlayer()) return " (player)";
        return dep.isAllEver() ? " (all ever)" : " (all online)";
    }

    private static Set<UUID> getAllKnownPlayers() {
        return IndividualStageData.SERVER_CACHE.keySet();
    }
}
