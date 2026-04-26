package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public class TooltipEventHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!Config.CLIENT.showTooltips.get()) return;

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

                event.getToolTip().add(Component.literal("Progress: ")
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

                event.getToolTip().add(Component.literal("Remaining Time: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(timeDisplay).withStyle(ChatFormatting.YELLOW)));
            }
            return;
        }
        // --- ENDE RESEARCH BOOK LOGIK ---

        // --- AB HIER: NORMALER LOCKED ITEM CHECK ---
        ResourceLocation itemLocation = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
                    stack.getItem().builtInRegistryHolder().tags()
                            .anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

            if (isListed) {
                totalRequiredStages.add(stage);
                if (!ClientStageCache.isStageUnlocked(stageID)) {
                    isCurrentlyLocked = true;
                }
            }
        }

        // Dual-phase phase-1 indicator
        if (isCurrentlyLocked && StageLockHelper.isDualPhaseGloballyLockedClient(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.historystages.dual_phase_lock")
                    .withStyle(ChatFormatting.GOLD));
            event.getToolTip().add(Component.translatable("tooltip.historystages.dual_phase_lock_desc")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        if (isCurrentlyLocked) {
            if (Config.CLIENT.showStageName.get()) {
                event.getToolTip().add(Component.literal("Required Progress:").withStyle(ChatFormatting.DARK_RED));

                for (StageEntry stage : totalRequiredStages) {
                    String stageID = StageManager.getStages().entrySet().stream()
                            .filter(e -> e.getValue().equals(stage))
                            .map(Map.Entry::getKey).findFirst().orElse("");

                    boolean unlocked = ClientStageCache.isStageUnlocked(stageID);
                    boolean showAll = Config.CLIENT.showAllUntilComplete.get();

                    if (totalRequiredStages.size() > 1 && showAll) {
                        ChatFormatting statusColor = unlocked ? ChatFormatting.GREEN : ChatFormatting.RED;
                        String statusText = unlocked ? " (Unlocked)" : " (Locked)";

                        event.getToolTip().add(Component.literal(" • ")
                                .append(Component.literal(stage.getDisplayName()).withStyle(ChatFormatting.GOLD))
                                .append(Component.literal(statusText).withStyle(statusColor)));
                    } else if (!unlocked) {
                        event.getToolTip().add(Component.literal(" • ")
                                .append(Component.literal(stage.getDisplayName()).withStyle(ChatFormatting.GOLD)));
                    }
                }
            } else {
                event.getToolTip().add(Component.literal("This item is currently locked!")
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
                    stack.getItem().builtInRegistryHolder().tags()
                            .anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

            if (isListed) {
                individualRequiredStages.add(stage);
                if (!ClientIndividualStageCache.isStageUnlocked(stageID)) {
                    isIndividuallyLocked = true;
                }
            }
        }

        if (isIndividuallyLocked && Config.CLIENT.showIndividualTooltips.get()) {
            if (Config.CLIENT.showStageName.get()) {
                event.getToolTip().add(Component.literal("Required Individual Progress:").withStyle(ChatFormatting.DARK_RED));

                for (StageEntry stage : individualRequiredStages) {
                    String stageID = StageManager.getIndividualStages().entrySet().stream()
                            .filter(e -> e.getValue().equals(stage))
                            .map(Map.Entry::getKey).findFirst().orElse("");

                    boolean unlocked = ClientIndividualStageCache.isStageUnlocked(stageID);
                    boolean showAll = Config.CLIENT.showAllUntilComplete.get();

                    if (individualRequiredStages.size() > 1 && showAll) {
                        ChatFormatting statusColor = unlocked ? ChatFormatting.GREEN : ChatFormatting.RED;
                        String statusText = unlocked ? " (Unlocked)" : " (Locked)";

                        event.getToolTip().add(Component.literal(" • ")
                                .append(Component.literal(stage.getDisplayName()).withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(statusText).withStyle(statusColor)));
                    } else if (!unlocked) {
                        event.getToolTip().add(Component.literal(" • ")
                                .append(Component.literal(stage.getDisplayName()).withStyle(ChatFormatting.GRAY)));
                    }
                }
            } else {
                event.getToolTip().add(Component.literal("This item is individually locked!")
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
