package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.dependency.RequirementDisplay;
import net.bananemdnsa.historystages.data.dependency.EntityKillDep;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;

/** Mobs the player has to have killed, counted from the vanilla kill statistic. */
public class EntityKillRequirement implements Requirement {

    @Override
    public String id() {
        return "entity_kill";
    }

    @Override
    public String tabLangKey() {
        return "editor.historystages.dep.tab.entity_kills";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.entity_kills";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.kills";
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
        return !group.getEntityKills().isEmpty();
    }

    @Override
    public List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<RequirementResult.EntryResult> results = new ArrayList<>();
        for (EntityKillDep kill : group.getEntityKills()) {
            int current = ctx.player() != null ? getKillCount(ctx.player(), kill.getEntityId()) : 0;
            boolean met = current >= kill.getCount();
            String entityName = getEntityDisplayName(kill.getEntityId());
            results.add(new RequirementResult.EntryResult("entity_kill", kill.getEntityId(),
                    kill.getCount() + "x " + entityName, met, current, kill.getCount()));
        }
        return results;
    }

    private static int getKillCount(ServerPlayer player, String entityId) {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null) return 0;
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(rl);
        if (entityType == null) return 0;
        ServerStatsCounter stats = player.getStats();
        return stats.getValue(Stats.ENTITY_KILLED.get(entityType));
    }

    private static String getEntityDisplayName(String entityId) {
        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null) return entityId;
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(rl);
        if (entityType == null) return entityId;
        return entityType.getDescription().getString();
    }
}
