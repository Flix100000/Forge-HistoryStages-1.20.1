package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBooster;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.SearchHiddenContents;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TooltipEventHandler {

    @SubscribeEvent
    public static void onBoosterTooltip(ItemTooltipEvent event) {
        if (!Config.CLIENT.showBoosterTooltips.get()) return;
        if (ResearchBoosterRegistry.all().isEmpty()) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return;

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
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
        if (booster.minTier() > 1
                || booster.tierMode() == net.bananemdnsa.historystages.research.TierMode.EXACT) {
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
        if (!Config.CLIENT.showTooltips.get()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // --- LOGIK FÜR DAS RESEARCH SCROLL ---
        if (stack.is(net.bananemdnsa.historystages.init.ModItems.RESEARCH_SCROLL.get())) {
            if (stack.hasTag() && stack.getTag().contains("ResearchProgress")) {
                int progress = stack.getTag().getInt("ResearchProgress");
                // MaxProgress aus NBT laden (wird von BlockEntity gesetzt) oder Fallback auf 20s (400 Ticks)
                int maxProgress = stack.getTag().contains("MaxProgress") ? stack.getTag().getInt("MaxProgress") : 400;

                // 1. Berechnung Progress %
                int percent = (int) Math.min(100, ((double) progress / maxProgress * 100));

                event.getToolTip().add(Component.translatable("tooltip.historystages.scroll.progress")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(percent + "%").withStyle(ChatFormatting.GREEN)));

                // 2. Berechnung Remaining Time
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


        // --- AB HIER: NORMALER LOCKED ITEM CHECK ---
        ResourceLocation itemLocation = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemLocation == null) return;

        String itemID = itemLocation.toString();
        String modID = itemLocation.getNamespace();

        List<StageEntry> totalRequiredStages = new ArrayList<>();
        boolean isCurrentlyLocked = false;

        for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
            StageEntry stage = entry.getValue();
            String stageID = entry.getKey();

            boolean isListed = (stage.getMods().contains(modID) && !stage.isModExcepted(itemID, stack)) ||
                    stage.getItems().contains(itemID) ||
                    matchesNbtItem(stage, itemID, stack) ||
                    stack.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

            if (isListed) {
                totalRequiredStages.add(stage);
                if (!ClientStageCache.isStageUnlocked(stageID)) {
                    isCurrentlyLocked = true;
                }
            }
        }

        // Dual-Phase Phase 1 indicator
        if (isCurrentlyLocked && StageLockHelper.isDualPhaseGloballyLockedClient(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.historystages.dual_phase_lock")
                    .withStyle(ChatFormatting.GOLD));
            event.getToolTip().add(Component.translatable("tooltip.historystages.dual_phase_lock_desc")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        if (isCurrentlyLocked) {
            if (Config.CLIENT.showStageName.get()) {
                event.getToolTip().add(Component.translatable("tooltip.historystages.required_progress").withStyle(ChatFormatting.DARK_RED));

                for (StageEntry stage : totalRequiredStages) {
                    String stageID = StageManager.getStages().entrySet().stream()
                            .filter(e -> e.getValue().equals(stage))
                            .map(Map.Entry::getKey).findFirst().orElse("");

                    boolean unlocked = ClientStageCache.isStageUnlocked(stageID);
                    boolean showAll = Config.CLIENT.showAllUntilComplete.get();

                    if (totalRequiredStages.size() > 1 && showAll) {
                        ChatFormatting statusColor = unlocked ? ChatFormatting.GREEN : ChatFormatting.RED;
                        String statusKey = unlocked ? "tooltip.historystages.status.unlocked" : "tooltip.historystages.status.locked";

                        event.getToolTip().add(Component.literal(" • ")
                                .append(MutableComponent.create(new SearchHiddenContents(stage.getDisplayName())).withStyle(ChatFormatting.GOLD))
                                .append(Component.translatable(statusKey).withStyle(statusColor)));
                    } else if (!unlocked) {
                        event.getToolTip().add(Component.literal(" • ")
                                .append(MutableComponent.create(new SearchHiddenContents(stage.getDisplayName())).withStyle(ChatFormatting.GOLD)));
                    }
                }
            } else {
                event.getToolTip().add(Component.translatable("tooltip.historystages.item_locked")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            }
        }

        // --- INDIVIDUAL STAGES TOOLTIP ---
        List<StageEntry> individualRequiredStages = new ArrayList<>();
        boolean isIndividuallyLocked = false;

        for (Map.Entry<String, StageEntry> entry : StageManager.getIndividualStages().entrySet()) {
            StageEntry stage = entry.getValue();
            String stageID = entry.getKey();

            boolean isListed = (stage.getMods().contains(modID) && !stage.isModExcepted(itemID, stack)) ||
                    stage.getItems().contains(itemID) ||
                    matchesNbtItem(stage, itemID, stack) ||
                    stack.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

            if (isListed) {
                individualRequiredStages.add(stage);
                if (!ClientIndividualStageCache.isStageUnlocked(stageID)) {
                    isIndividuallyLocked = true;
                }
            }
        }

        if (isIndividuallyLocked && Config.CLIENT.showIndividualTooltips.get()) {
            if (Config.CLIENT.showStageName.get()) {
                event.getToolTip().add(Component.translatable("tooltip.historystages.required_individual_progress").withStyle(ChatFormatting.DARK_RED));

                for (StageEntry stage : individualRequiredStages) {
                    String stageID = StageManager.getIndividualStages().entrySet().stream()
                            .filter(e -> e.getValue().equals(stage))
                            .map(Map.Entry::getKey).findFirst().orElse("");

                    boolean unlocked = ClientIndividualStageCache.isStageUnlocked(stageID);
                    boolean showAll = Config.CLIENT.showAllUntilComplete.get();

                    if (individualRequiredStages.size() > 1 && showAll) {
                        ChatFormatting statusColor = unlocked ? ChatFormatting.GREEN : ChatFormatting.RED;
                        String statusKey = unlocked ? "tooltip.historystages.status.unlocked" : "tooltip.historystages.status.locked";

                        event.getToolTip().add(Component.literal(" • ")
                                .append(MutableComponent.create(new SearchHiddenContents(stage.getDisplayName())).withStyle(ChatFormatting.GRAY))
                                .append(Component.translatable(statusKey).withStyle(statusColor)));
                    } else if (!unlocked) {
                        event.getToolTip().add(Component.literal(" • ")
                                .append(MutableComponent.create(new SearchHiddenContents(stage.getDisplayName())).withStyle(ChatFormatting.GRAY)));
                    }
                }
            } else {
                event.getToolTip().add(Component.translatable("tooltip.historystages.item_individually_locked")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
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
}