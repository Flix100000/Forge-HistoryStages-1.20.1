package net.bananemdnsa.historystages.data.dependency.requirements;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.DependencyProgress;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.dependency.Requirement;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.api.dependency.RequirementDisplay;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Items the player has to hand in — tracked via deposited NBT, not live inventory.
 *
 * <p>Progress is filed per group by {@link DependencyProgress}, under the group's id rather than
 * its position. Reordering or deleting groups in the editor used to re-attribute everything a
 * player had already deposited; a group written before ids existed still falls back to its
 * position, so the keys on existing scrolls are unchanged.
 */
public class ItemRequirement implements Requirement {

    @Override
    public String id() {
        return "item";
    }

    @Override
    public String tabLangKey() {
        return "editor.historystages.dep.tab.items";
    }

    @Override
    public String tooltipLangKey() {
        return "editor.historystages.dep.tooltip.items";
    }

    @Override
    public String sectionLangKey() {
        return "editor.historystages.graph.section.items";
    }

    @Override
    public RequirementDisplay.Kind displayKind() {
        return RequirementDisplay.Kind.DEPOSITED;
    }

    @Override
    public boolean declaredIn(DependencyGroup group) {
        return !group.getItems().isEmpty();
    }

    @Override
    public List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<RequirementResult.EntryResult> results = new ArrayList<>();
        for (DependencyItem item : group.getItems()) {
            int original = item.getCount();
            int required = BoosterUtil.effectiveCount(original, ctx.costReduction());
            int current = (ctx.depositedData() != null)
                    ? ctx.depositedData().getInt(ctx.progressKey(DependencyProgress.itemSuffix(item.getId())))
                    : 0;
            boolean met = current >= required;
            String itemName = getItemDisplayName(item.getId());
            int originalForUi = (required == original) ? 0 : original;
            results.add(new RequirementResult.EntryResult("item", item.getId(),
                    required + "x " + itemName, met, current, required, originalForUi, false));
        }
        return results;
    }

    private static String getItemDisplayName(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return itemId;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null) return itemId;
        return item.getDescription().getString();
    }
}
