package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DimensionLockHandler {

    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation targetDim = event.getDimension().location();
        String dimId = targetDim.toString();

        // Check global stages
        List<String> requiredStageIds = StageManager.getAllStagesForDimension(dimId);
        List<String> lockedStages = new ArrayList<>();
        for (String stageId : requiredStageIds) {
            if (!StageData.SERVER_CACHE.contains(stageId)) {
                lockedStages.add(stageId);
            }
        }

        // Check individual stages
        List<String> individualStageIds = StageManager.getAllIndividualStagesForDimension(dimId);
        if (!individualStageIds.isEmpty()) {
            java.util.Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(player.getUUID(), java.util.Collections.emptySet());
            for (String stageId : individualStageIds) {
                if (!playerStages.contains(stageId)) {
                    lockedStages.add(stageId);
                }
            }
        }

        if (requiredStageIds.isEmpty() && individualStageIds.isEmpty()) return;

        if (!lockedStages.isEmpty()) {
            event.setCanceled(true);
            DebugLogger.runtime("Dimension Lock", player.getName().getString(),
                    "Blocked travel to '" + dimId + "' — missing stages: " + lockedStages);

            if (Config.CLIENT.dimShowChat.get()) {
                MutableComponent chatMsg = Component.translatable("message.historystages.dimension_locked");
                if (Config.CLIENT.dimShowStagesInChat.get()) {
                    for (String stageId : lockedStages) {
                        StageEntry stageEntry = StageManager.getStages().get(stageId);
                        if (stageEntry == null) {
                            stageEntry = StageManager.getIndividualStages().get(stageId);
                        }
                        String displayName = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
                        chatMsg.append(Component.translatable("message.historystages.locked_stage", displayName));
                    }
                }
                player.sendSystemMessage(chatMsg);
            }

            if (Config.CLIENT.dimUseActionbar.get()) {
                player.displayClientMessage(
                        Component.translatable("message.historystages.dimension_unknown")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                        true
                );
            }
        }
    }
}