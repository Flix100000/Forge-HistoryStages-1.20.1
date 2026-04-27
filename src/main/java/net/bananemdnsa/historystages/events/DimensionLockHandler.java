package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.LockFeedbackPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
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

            List<String> displayNames = new ArrayList<>(lockedStages.size());
            for (String stageId : lockedStages) {
                StageEntry stageEntry = StageManager.getStages().get(stageId);
                if (stageEntry == null) {
                    stageEntry = StageManager.getIndividualStages().get(stageId);
                }
                displayNames.add(stageEntry != null ? stageEntry.getDisplayName() : stageId);
            }

            PacketHandler.sendLockFeedbackToPlayer(
                    new LockFeedbackPacket(LockFeedbackPacket.KIND_DIMENSION, displayNames),
                    player
            );
        }
    }
}
