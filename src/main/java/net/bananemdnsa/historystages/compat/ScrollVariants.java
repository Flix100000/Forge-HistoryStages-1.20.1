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

    /**
     * Ids of every stage that has a scroll at all, in the same order and with the same AUTO /
     * TEMPORARY exclusion {@link #buildAllStageScrolls()} applies. Recipe-viewer plugins need the
     * ids rather than finished stacks, because they build both the open and the sealed side.
     */
    public static List<String> scrollableStageIds() {
        List<String> ids = new ArrayList<>();
        addIdsFor(StageManager.getStages(), ids);
        addIdsFor(StageManager.getIndividualStages(), ids);
        return ids;
    }

    private static void addIdsFor(Map<String, StageEntry> stages, List<String> out) {
        for (var entry : stages.entrySet()) {
            if (entry.getValue().getMode().usesAutoTrigger()) continue;
            out.add(entry.getKey());
        }
    }

    private static void addScrollsFor(Map<String, StageEntry> stages, List<ItemStack> out) {
        for (var entry : stages.entrySet()) {
            if (entry.getValue().getMode().usesAutoTrigger()) continue;
            out.add(createScroll(entry.getKey()));
        }
    }

    /**
     * A scroll exactly as it is first obtained: the stage tag and nothing else. No owner,
     * no progress, no deposits — the single definition of "fresh", used by the creative
     * tab, the JEI/EMI variant lists and the pedestal's refill.
     */
    public static ItemStack createScroll(String stageId) {
        ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
        CompoundTag nbt = new CompoundTag();
        nbt.putString(STAGE_RESEARCH_KEY, stageId);
        scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        return scroll;
    }

    /** The keepsake an {@code open} completion leaves behind, tagged with its stage. */
    public static ItemStack createOpenScroll(String stageId) {
        ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL_OPEN.get());
        CompoundTag nbt = new CompoundTag();
        nbt.putString(STAGE_RESEARCH_KEY, stageId);
        scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        return scroll;
    }
}
