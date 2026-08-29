package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.lock.category.BuiltInLockMatching;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.CategoryLockIndexes;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBooster;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.client.display.HiddenDisplayResolver;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.bananemdnsa.historystages.util.SearchHiddenContents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public class TooltipEventHandler {

    @SubscribeEvent
    public static void onBoosterTooltip(ItemTooltipEvent event) {
        if (!Config.VISUAL.showBoosterTooltips.get()) return;
        if (ResearchBoosterRegistry.all().isEmpty()) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return;

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        ResearchBooster booster = ResearchBoosterRegistry.get(id).orElse(null);
        if (booster == null) return;

        event.getToolTip().add(Component.translatable("tooltip.historystages.research_booster.header")
                .withStyle(ChatFormatting.AQUA));
        if (booster.hasSpeed()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.historystages.research_booster.speed",
                    BoosterUtil.formatMultiplier(booster.speedReduction()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (booster.hasCost()) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.historystages.research_booster.cost",
                    BoosterUtil.percent(booster.costReduction()))
                    .withStyle(ChatFormatting.GRAY));
        }
        // Tier gating — only show when it actually narrows the booster
        // (tier > 1 or mode == EXACT).
        if (booster.minTier() > 1 || booster.tierMode() == net.bananemdnsa.historystages.research.TierMode.EXACT) {
            String key = booster.tierMode() == net.bananemdnsa.historystages.research.TierMode.EXACT
                    ? "tooltip.historystages.research_booster.tier.exact"
                    : "tooltip.historystages.research_booster.tier.min";
            event.getToolTip().add(Component.translatable(key,
                    net.bananemdnsa.historystages.research.TierMatcher.roman(booster.minTier()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!Config.VISUAL.showTooltips.get()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // --- LOGIK FÜR DAS RESEARCH SCROLL ---
        if (stack.is(net.bananemdnsa.historystages.init.ModItems.RESEARCH_SCROLL.get())) {
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            var nbt = customData.copyTag();

            if (nbt.contains("ResearchProgress")) {
                int progress = nbt.getInt("ResearchProgress");
                int maxProgress = nbt.contains("MaxProgress") ? nbt.getInt("MaxProgress") : 400;

                int percent = (int) Math.min(100, ((double) progress / maxProgress * 100));

                event.getToolTip().add(Component.translatable("tooltip.historystages.scroll.progress")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(percent + "%").withStyle(ChatFormatting.GREEN)));

                int remainingTicks = Math.max(0, maxProgress - progress);
                int remainingSeconds = (remainingTicks / 20) + (remainingTicks % 20 > 0 ? 1 : 0);
                if (percent >= 100) remainingSeconds = 0;

                String timeDisplay;
                if (remainingSeconds >= 60) {
                    int mins = remainingSeconds / 60;
                    int secs = remainingSeconds % 60;
                    timeDisplay = mins + "min " + secs + "s";
                } else {
                    timeDisplay = remainingSeconds + "s";
                }

                event.getToolTip().add(Component.translatable("tooltip.historystages.scroll.remaining_time")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(timeDisplay).withStyle(ChatFormatting.YELLOW)));
            }
            return;
        }
        // --- ENDE RESEARCH BOOK LOGIK ---

        // --- HIDDEN DISPLAY: strip/replace the item's own tooltip lines ---
        HiddenDisplayResolver.Resolved hidden = HiddenDisplayResolver.resolve(stack);
        if (hidden.changesTooltip()) {
            applyTooltipEffect(event.getToolTip(), hidden);
        }
        // Lock hints are the mod's own lines below; suppress them when configured.
        if (!hidden.showLockHints()) return;

        // --- AB HIER: NORMALER LOCKED ITEM CHECK ---
        ResourceLocation itemLocation = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemLocation == null) return;

        String itemID = itemLocation.toString();
        String modID = itemLocation.getNamespace();

        // Both scopes ask the same question about the same item, so both go through the
        // relevance index first: it names the handful of stages that mention this item at all,
        // and only those get the exact test. Walking every stage instead cost three fresh
        // ArrayLists per stage per tooltip frame - getMods, getItems and getNbtFreeTags each
        // rebuild theirs from a stream on every call.
        Item item = stack.getItem();

        List<GatingStage> totalRequiredStages = gatingStages(
                CategoryLockIndexes.globalCandidates(itemID, modID, item),
                StageManager.getStages(), itemID, modID, stack);
        boolean isCurrentlyLocked = anyLocked(totalRequiredStages, ClientStageCache::isStageUnlocked);

        // Dual-phase phase-1 indicator
        if (isCurrentlyLocked && StageLockHelper.isDualPhaseGloballyLockedClient(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.historystages.dual_phase_lock")
                    .withStyle(ChatFormatting.GOLD));
            event.getToolTip().add(Component.translatable("tooltip.historystages.dual_phase_lock_desc")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        if (isCurrentlyLocked) {
            appendStageLines(event.getToolTip(), totalRequiredStages,
                    ClientStageCache::isStageUnlocked, ChatFormatting.GOLD,
                    "tooltip.historystages.required_progress",
                    "tooltip.historystages.item_locked");
        }

        // --- INDIVIDUAL STAGES TOOLTIP ---
        List<GatingStage> individualRequiredStages = gatingStages(
                CategoryLockIndexes.individualCandidates(itemID, modID, item),
                StageManager.getIndividualStages(), itemID, modID, stack);
        boolean isIndividuallyLocked =
                anyLocked(individualRequiredStages, ClientIndividualStageCache::isStageUnlocked);

        if (isIndividuallyLocked && Config.VISUAL.showIndividualTooltips.get()) {
            appendStageLines(event.getToolTip(), individualRequiredStages,
                    ClientIndividualStageCache::isStageUnlocked, ChatFormatting.GRAY,
                    "tooltip.historystages.required_individual_progress",
                    "tooltip.historystages.item_individually_locked");
        }
    }

    /** A stage that gates this item, carried with its id so nothing has to look the id up again. */
    private record GatingStage(String id, StageEntry stage) {}

    /**
     * The candidate stages that really list this item.
     *
     * <p>{@code candidateIds} is the relevance index's over-approximation: it may name a stage
     * that turns out not to match, never omit one that does. {@link #listsItem} settles it.
     */
    private static List<GatingStage> gatingStages(Collection<String> candidateIds,
                                                  Map<String, StageEntry> stages,
                                                  String itemID, String modID, ItemStack stack) {
        if (candidateIds.isEmpty()) return List.of();

        List<GatingStage> found = new ArrayList<>(candidateIds.size());
        for (String stageID : candidateIds) {
            StageEntry stage = stages.get(stageID);
            if (stage != null && listsItem(stage, itemID, modID, stack)) {
                found.add(new GatingStage(stageID, stage));
            }
        }
        return found;
    }

    /** The exact "does this stage gate this item" test, unchanged from the old inline version. */
    private static boolean listsItem(StageEntry stage, String itemID, String modID, ItemStack stack) {
        return (stage.getMods().contains(modID) && !stage.isModExcepted(itemID, stack))
                || stage.getItems().contains(itemID)
                || matchesNbtItem(stage, itemID, stack)
                || stack.getItem().builtInRegistryHolder().tags()
                        .anyMatch(tag -> stage.getNbtFreeTags().contains(tag.location().toString()))
                || matchesNbtTag(stage, stack);
    }

    private static boolean anyLocked(List<GatingStage> gating, Predicate<String> isUnlocked) {
        for (GatingStage entry : gating) {
            if (!isUnlocked.test(entry.id())) return true;
        }
        return false;
    }

    /**
     * The "you still need" block: a header, then one line per gating stage.
     *
     * <p>One method for both scopes. They differ only in which cache answers, which colour the
     * bullet takes and which two lang keys are used - keeping two copies is how the global and
     * individual halves drifted apart in the first place.
     */
    private static void appendStageLines(List<Component> tooltip, List<GatingStage> gating,
                                         Predicate<String> isUnlocked, ChatFormatting bulletColor,
                                         String headerKey, String shortKey) {
        if (!Config.VISUAL.showStageName.get()) {
            tooltip.add(Component.translatable(shortKey)
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            return;
        }

        tooltip.add(Component.translatable(headerKey).withStyle(ChatFormatting.DARK_RED));

        boolean showAll = Config.VISUAL.showAllUntilComplete.get();
        for (GatingStage entry : gating) {
            boolean unlocked = isUnlocked.test(entry.id());

            if (gating.size() > 1 && showAll) {
                ChatFormatting statusColor = unlocked ? ChatFormatting.GREEN : ChatFormatting.RED;
                String statusKey = unlocked
                        ? "tooltip.historystages.status.unlocked"
                        : "tooltip.historystages.status.locked";
                tooltip.add(Component.literal(" • ")
                        .append(MutableComponent.create(new SearchHiddenContents(entry.stage().getDisplayName()))
                                .withStyle(bulletColor))
                        .append(Component.translatable(statusKey).withStyle(statusColor)));
            } else if (!unlocked) {
                tooltip.add(Component.literal(" • ")
                        .append(MutableComponent.create(new SearchHiddenContents(entry.stage().getDisplayName()))
                                .withStyle(bulletColor)));
            }
        }
    }

    /**
     * Strips the item's own tooltip lines (everything below the name) and, for REPLACE,
     * substitutes the configured text. When the name itself is hidden, drops the now-empty
     * name line too so there is no blank gap.
     */
    private static void applyTooltipEffect(List<Component> tooltip, HiddenDisplayResolver.Resolved hidden) {
        if (tooltip.isEmpty()) return;
        // Always keep the name line (index 0) so the tooltip never becomes an empty list,
        // which would crash the vanilla tooltip renderer.
        while (tooltip.size() > 1) {
            tooltip.remove(tooltip.size() - 1);
        }
        if (hidden.tooltipMode() == net.bananemdnsa.historystages.data.display.DisplayMode.REPLACE
                && !hidden.tooltipText().isEmpty()) {
            for (String line : hidden.tooltipText().split("\n", -1)) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static boolean matchesNbtItem(StageEntry stage, String itemID, ItemStack stack) {
        for (ItemEntry itemEntry : stage.getItemEntries()) {
            if (itemEntry.getId().equals(itemID) && itemEntry.hasNbt()) {
                if (NbtMatcher.matches(stack, itemEntry.getNbt())) return true;
            }
        }
        return false;
    }

    private static boolean matchesNbtTag(StageEntry stage, ItemStack stack) {
        net.minecraft.world.item.Item item = stack.getItem();
        for (net.bananemdnsa.historystages.data.lock.NamedLockEntry tagEntry : stage.getTagEntries()) {
            if (tagEntry.hasNbt() && BuiltInLockMatching.tagEntryMatches(tagEntry, stack, item)) return true;
        }
        return false;
    }
}
