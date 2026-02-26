package net.felix.historystages.events;

import net.felix.historystages.Config;
import net.felix.historystages.HistoryStages;
import net.felix.historystages.data.StageEntry;
import net.felix.historystages.data.StageManager;
import net.felix.historystages.util.ClientStageCache;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!Config.CLIENT.showTooltips.get()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // --- LOGIK FÜR DAS RESEARCH BOOK ---
        if (stack.is(net.felix.historystages.init.ModItems.RESEARCH_BOOK.get())) {
            if (stack.hasTag() && stack.getTag().contains("ResearchProgress")) {
                int progress = stack.getTag().getInt("ResearchProgress");
                // MaxProgress aus NBT laden (wird von BlockEntity gesetzt) oder Fallback auf 20s (400 Ticks)
                int maxProgress = stack.getTag().contains("MaxProgress") ? stack.getTag().getInt("MaxProgress") : 400;

                // 1. Berechnung Progress %
                int percent = (int) Math.min(100, ((double) progress / maxProgress * 100));

                event.getToolTip().add(Component.literal("Progress: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(percent + "%").withStyle(ChatFormatting.GREEN)));

                // 2. Berechnung Remaining Time
                int remainingTicks = Math.max(0, maxProgress - progress);
                int remainingSeconds = (remainingTicks / 20) + (remainingTicks % 20 > 0 ? 1 : 0);
                if (percent >= 100) remainingSeconds = 0;

                event.getToolTip().add(Component.literal("Remaining Time: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(remainingSeconds + "s").withStyle(ChatFormatting.YELLOW)));

                // 3. Target Stage
                if (stack.getTag().contains("StageResearch")) {
                    String stageId = stack.getTag().getString("StageResearch");
                    StageEntry entry = StageManager.getStages().get(stageId);
                    String displayName = (entry != null) ? entry.getDisplayName() : stageId;

                    event.getToolTip().add(Component.literal("Target: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(displayName).withStyle(ChatFormatting.GOLD)));
                }
            } else {
                event.getToolTip().add(Component.literal("Progress: 0%")
                        .withStyle(ChatFormatting.RED));
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

            boolean isListed = stage.getMods().contains(modID) ||
                    stage.getItems().contains(itemID) ||
                    stack.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

            if (isListed) {
                totalRequiredStages.add(stage);
                if (!ClientStageCache.isStageUnlocked(stageID)) {
                    isCurrentlyLocked = true;
                }
            }
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
    }
}