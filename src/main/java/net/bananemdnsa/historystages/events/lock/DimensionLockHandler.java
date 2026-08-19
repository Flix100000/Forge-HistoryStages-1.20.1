package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import net.bananemdnsa.historystages.network.clientbound.LockFeedbackPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class DimensionLockHandler {

    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation targetDim = event.getDimension().location();
        String dimId = targetDim.toString();

        List<String> requiredStageIds =
                StageLocks.engine().gatingStagesForDimension(dimId, StageScope.GLOBAL);
        List<String> individualStageIds =
                StageLocks.engine().gatingStagesForDimension(dimId, StageScope.INDIVIDUAL);

        List<String> lockedStages =
                new ArrayList<>(LockResolution.missingStages(requiredStageIds, StageLocks.serverGlobal()));
        lockedStages.addAll(LockResolution.missingStages(
                individualStageIds, StageLocks.serverIndividual(player.getUUID())));

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
