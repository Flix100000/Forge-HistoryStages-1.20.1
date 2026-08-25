package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Advancements the player has to have earned — individual player only. */
public class AdvancementRequirement implements Requirement {

    @Override
    public String id() {
        return "advancement";
    }

    @Override
    public String tabLangKey() {
        return "editor.historystages.dep.tab.advancements";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.advancements";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.advancements";
    }

    @Override
    public Set<StageScope> supportedScopes() {
        return Set.of(StageScope.INDIVIDUAL);
    }

    @Override
    public boolean declaredIn(DependencyGroup group) {
        return !group.getAdvancements().isEmpty();
    }

    @Override
    public List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<RequirementResult.EntryResult> results = new ArrayList<>();
        for (String advId : group.getAdvancements()) {
            boolean met = ctx.player() != null && checkAdvancement(ctx.player(), advId);
            results.add(new RequirementResult.EntryResult("advancement", advId, advId, met, met ? 1 : 0, 1));
        }
        return results;
    }

    private static boolean checkAdvancement(ServerPlayer player, String advancementId) {
        ResourceLocation rl = ResourceLocation.tryParse(advancementId);
        if (rl == null || player.getServer() == null) return false;
        AdvancementHolder holder = player.getServer().getAdvancements().get(rl);
        if (holder == null) return false;
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
