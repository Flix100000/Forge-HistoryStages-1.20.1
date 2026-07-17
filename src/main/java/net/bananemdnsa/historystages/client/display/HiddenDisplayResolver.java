package net.bananemdnsa.historystages.client.display;

import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.bananemdnsa.historystages.data.display.HiddenDisplayConfig;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.display.TextOverrideHolder;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Client-side resolver that decides how a locked item's name and tooltip should be
 * displayed, based on the {@link HiddenDisplayConfig} of the stages that lock it.
 *
 * <p>Global and individual stages are resolved against their respective client caches and
 * share a single scoring pool, so an item may take its display from either kind.</p>
 *
 * <p>The hidden-display mode is stage-wide; only the REPLACE text varies per item via
 * overrides. When an item is locked by several stages, name and tooltip are resolved
 * <b>independently</b>; for each axis the winner is the stage that matches the item most
 * specifically — explicit item entry (3) &gt; tag (2) &gt; mod (1) — with ties broken on
 * stage iteration order, where global stages are visited first. A per-item REPLACE override
 * only applies when that axis won via an explicit item entry.</p>
 *
 * <p>Lock hints are resolved independently of name and tooltip: any matching locked stage
 * that disables them wins, so a stage can suppress the hints while leaving the item's own
 * name and tooltip untouched.</p>
 */
public final class HiddenDisplayResolver {

    private HiddenDisplayResolver() {}

    private static final int SCORE_ITEM = 3;
    private static final int SCORE_TAG = 2;
    private static final int SCORE_MOD = 1;

    /** Resolved name/tooltip effects for a single item. */
    public record Resolved(DisplayMode nameMode, String nameText,
                           DisplayMode tooltipMode, String tooltipText,
                           boolean showLockHints) {

        public static final Resolved NONE =
                new Resolved(DisplayMode.OFF, "", DisplayMode.OFF, "", true);

        public boolean changesName() { return nameMode != DisplayMode.OFF; }
        public boolean changesTooltip() { return tooltipMode != DisplayMode.OFF; }
    }

    public static Resolved resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Resolved.NONE;
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return Resolved.NONE;

        String itemId = res.toString();
        String modId = res.getNamespace();

        Accumulator acc = new Accumulator();
        // Global stages run first, so on an equal match score the global stage keeps the win.
        collect(StageManager.getStages(), ClientStageCache::isStageUnlocked, itemId, modId, stack, acc);
        collect(StageManager.getIndividualStages(), ClientIndividualStageCache::isStageUnlocked,
                itemId, modId, stack, acc);

        if (acc.nameMode == DisplayMode.OFF && acc.tooltipMode == DisplayMode.OFF && acc.showLockHints) {
            return Resolved.NONE;
        }
        return new Resolved(acc.nameMode, acc.nameText, acc.tooltipMode, acc.tooltipText, acc.showLockHints);
    }

    /** Mutable carry for a resolve pass, so global and individual stages share one scoring pool. */
    private static final class Accumulator {
        int bestNameScore;
        int bestTooltipScore;
        DisplayMode nameMode = DisplayMode.OFF;
        String nameText = "";
        DisplayMode tooltipMode = DisplayMode.OFF;
        String tooltipText = "";
        boolean showLockHints = true;
    }

    private static void collect(Map<String, StageEntry> stages,
                                Predicate<String> isUnlocked,
                                String itemId, String modId, ItemStack stack,
                                Accumulator acc) {
        for (Map.Entry<String, StageEntry> e : stages.entrySet()) {
            StageEntry stage = e.getValue();
            HiddenDisplayConfig cfg = stage.getHiddenDisplay();
            if (cfg.isNoop()) continue;
            if (isUnlocked.test(e.getKey())) continue;

            TextOverrideHolder holder;
            int score;
            ItemEntry itemMatch = matchedItemEntry(stage, itemId, stack);
            if (itemMatch != null) {
                holder = itemMatch;
                score = SCORE_ITEM;
            } else {
                NamedLockEntry tagMatch = matchedTag(stage, stack);
                if (tagMatch != null) {
                    holder = tagMatch;
                    score = SCORE_TAG;
                } else {
                    NamedLockEntry modMatch = matchedMod(stage, itemId, modId, stack);
                    if (modMatch == null) continue;
                    holder = modMatch;
                    score = SCORE_MOD;
                }
            }

            if (!cfg.isShowLockHints()) acc.showLockHints = false;

            // Strict > keeps the first stage on ties (iteration order).
            if (cfg.getNameMode() != DisplayMode.OFF && score > acc.bestNameScore) {
                acc.bestNameScore = score;
                acc.nameMode = cfg.getNameMode();
                acc.nameText = resolveText(cfg.getNameText(), holder.getNameTextOverride());
            }
            if (cfg.getTooltipMode() != DisplayMode.OFF && score > acc.bestTooltipScore) {
                acc.bestTooltipScore = score;
                acc.tooltipMode = cfg.getTooltipMode();
                acc.tooltipText = resolveText(cfg.getTooltipText(), holder.getTooltipTextOverride());
            }
        }
    }

    /** Override wins over the stage default; either may be empty. */
    private static String resolveText(String stageDefault, String override) {
        if (override != null) return override;
        return stageDefault != null ? stageDefault : "";
    }

    /** Returns the explicit item entry matching this stack (carrying overrides), or null. */
    private static ItemEntry matchedItemEntry(StageEntry stage, String itemId, ItemStack stack) {
        for (ItemEntry ie : stage.getItemEntries()) {
            if (!ie.getId().equals(itemId)) continue;
            if (!ie.hasNbt() || NbtMatcher.matches(stack, ie.getNbt())) return ie;
        }
        return null;
    }

    private static NamedLockEntry matchedTag(StageEntry stage, ItemStack stack) {
        Item item = stack.getItem();
        for (NamedLockEntry tagEntry : stage.getTagEntries()) {
            if (net.bananemdnsa.historystages.data.StageManager.tagEntryMatches(stack, item, tagEntry)) return tagEntry;
        }
        return null;
    }

    private static NamedLockEntry matchedMod(StageEntry stage, String itemId, String modId, ItemStack stack) {
        if (stage.isModExcepted(itemId, stack)) return null;
        for (NamedLockEntry modEntry : stage.getModEntries()) {
            if (modEntry.getId().equals(modId)) return modEntry;
        }
        return null;
    }
}
