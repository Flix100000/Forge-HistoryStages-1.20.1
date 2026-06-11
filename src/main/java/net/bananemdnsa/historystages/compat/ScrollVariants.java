package net.bananemdnsa.historystages.compat;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plugin-agnostic logic for research-scroll display variants, shared by the JEI
 * and EMI integrations so the {@code StageResearch} NBT handling stays in one place.
 */
public final class ScrollVariants {

    private ScrollVariants() {}

    /** The custom-data tag key that distinguishes scroll variants per stage. */
    public static final String STAGE_RESEARCH_KEY = "StageResearch";

    /**
     * Returns the {@code StageResearch} value of a scroll stack, or {@code null} if
     * the stack has no such tag. Used by JEI's subtype interpreter and EMI's
     * comparison to treat per-stage scrolls as distinct ingredients.
     */
    public static String readStageResearch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return tag.contains(STAGE_RESEARCH_KEY) ? tag.getString(STAGE_RESEARCH_KEY) : null;
    }

    /**
     * Builds one {@code RESEARCH_SCROLL} variant per non-auto-trigger stage (across
     * both the global and individual stage maps), each tagged with its stage id.
     */
    public static List<ItemStack> buildAllStageScrolls() {
        List<ItemStack> scrolls = new ArrayList<>();
        addScrollsFor(StageManager.getStages(), scrolls);
        addScrollsFor(StageManager.getIndividualStages(), scrolls);
        return scrolls;
    }

    private static void addScrollsFor(Map<String, StageEntry> stages, List<ItemStack> out) {
        for (var entry : stages.entrySet()) {
            if (entry.getValue().getMode().usesAutoTrigger()) continue;
            ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
            CompoundTag nbt = new CompoundTag();
            nbt.putString(STAGE_RESEARCH_KEY, entry.getKey());
            scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            out.add(scroll);
        }
    }
}
