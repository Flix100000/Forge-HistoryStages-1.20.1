package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.Requirement;
import net.bananemdnsa.historystages.data.dependency.RequirementContext;
import net.bananemdnsa.historystages.data.dependency.RequirementDisplay;
import net.bananemdnsa.historystages.data.dependency.StatDep;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/** Custom vanilla statistics the player has to have reached a minimum value on. */
public class StatRequirement implements Requirement {

    @Override
    public String id() {
        return "stat";
    }

    @Override
    public String tabLangKey() {
        return "editor.historystages.dep.tab.stats";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.stats";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.stats";
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
        return !group.getStats().isEmpty();
    }

    @Override
    public List<DependencyResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<DependencyResult.EntryResult> results = new ArrayList<>();
        for (StatDep stat : group.getStats()) {
            int current = ctx.player() != null ? getStatValue(ctx.player(), stat.getStatId()) : 0;
            boolean met = current >= stat.getMinValue();
            results.add(new DependencyResult.EntryResult("stat", stat.getStatId(),
                    stat.getStatId() + " >= " + stat.getMinValue(), met, current, stat.getMinValue()));
        }
        return results;
    }

    /**
     * The player's value for a custom stat named by id.
     *
     * <p>The registry lookup in the middle is not decoration. {@code StatType} keeps its stats in
     * an <strong>IdentityHashMap</strong>, so {@code Stats.CUSTOM.get(...)} only finds a stat when
     * handed the very {@code ResourceLocation} instance that was registered. A freshly parsed one
     * is equal but not identical, and the lookup silently creates a second, empty stat instead —
     * which read as zero however much the player had actually done, so a stat requirement could
     * never be fulfilled at all. {@code BuiltInRegistries.CUSTOM_STAT.get} hands back the
     * registered instance, and the identity map then finds it.
     *
     * <p>Found by {@code DependencyCheckerTests} on 2026-08-24, the first thing the GameTest
     * harness caught.
     */
    private static int getStatValue(ServerPlayer player, String statId) {
        ResourceLocation rl = ResourceLocation.tryParse(statId);
        if (rl == null) return 0;
        ResourceLocation registered = BuiltInRegistries.CUSTOM_STAT.get(rl);
        if (registered == null) return 0;
        try {
            return player.getStats().getValue(Stats.CUSTOM.get(registered));
        } catch (Exception e) {
            return 0;
        }
    }
}
