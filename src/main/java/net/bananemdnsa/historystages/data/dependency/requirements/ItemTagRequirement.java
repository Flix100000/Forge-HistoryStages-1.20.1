package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.api.dependency.RequirementDisplay;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.DependencyProgress;
import net.bananemdnsa.historystages.data.dependency.ItemTagResolution;
import net.bananemdnsa.historystages.research.BoosterUtil;

/**
 * Item tags the player has to hand in — "three ingots, any kind", until the first one decides
 * which kind.
 *
 * <p>The twin of {@link ItemRequirement} and deliberately close to it: same entry shape, same
 * booster reduction, progress filed the same way per group. What it adds is that an entry can
 * settle. The first matching item thrown into the pedestal is written to the scroll beside the
 * count, and from then on this entry demands that one item — otherwise a tag would be nothing
 * but a shorter way to write an OR of items, and a player could scrape together one of each.
 *
 * <p>The entry's id stays the tag, settled or not. Every screen finds its entry by type and id,
 * and an id that changed halfway through a research would leave the card it belongs to behind.
 * What the settled item changes is the label and the icon, never the identity.
 */
public class ItemTagRequirement implements Requirement {

    @Override
    public String id() {
        return "item_tag";
    }

    @Override
    public String tabLangKey() {
        return "editor.historystages.dep.tab.item_tags";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.item_tags";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.item_tags";
    }

    @Override
    public RequirementDisplay.Kind displayKind() {
        return RequirementDisplay.Kind.DEPOSITED;
    }

    @Override
    public boolean declaredIn(DependencyGroup group) {
        return !group.getItemTags().isEmpty();
    }

    @Override
    public List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<RequirementResult.EntryResult> results = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (DependencyItem tag : group.getItemTags()) {
            int original = tag.getCount();
            int required = BoosterUtil.effectiveCount(original, ctx.costReduction());

            int current = 0;
            String settled = null;
            if (ctx.depositedData() != null) {
                current = ctx.depositedData().getInt(
                        ctx.progressKey(DependencyProgress.itemTagSuffix(tag.getId())));
                settled = ctx.depositedData().getString(
                        ctx.progressKey(DependencyProgress.itemTagChoiceSuffix(tag.getId())));
            }

            // getString answers "" for an absent key; the entry says "not settled" with null, so
            // that nothing downstream has to know which of the two it is looking at.
            if (settled != null && settled.isEmpty()) settled = null;

            String name = ItemTagResolution.displayName(tag.getId(), settled, now);
            int originalForUi = (required == original) ? 0 : original;
            results.add(new RequirementResult.EntryResult("item_tag", tag.getId(),
                    required + "x " + name, current >= required, current, required,
                    originalForUi, false, settled));
        }
        return results;
    }
}
