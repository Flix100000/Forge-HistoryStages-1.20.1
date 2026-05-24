package net.bananemdnsa.historystages.events;

import net.astr0.historystages.api.HistoryStagesAPI;
import net.bananemdnsa.historystages.HistoryStages;
import net.astr0.historystages.api.StageDefinition;
import net.bananemdnsa.historystages.network.LockFeedbackPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DimensionLockHandler extends AbstractHandlerGroup {

    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation targetDim = event.getDimension().location();

        if (HistoryStagesAPI.DIMENSIONS.isLocked(targetDim, player)) {

            event.setCanceled(true);

            List<StageDefinition> lockedStages = HistoryStagesAPI.DIMENSIONS.getMissingStagesFor(targetDim, player);
            DebugLogger.runtime("Dimension Lock", player.getName().getString(),
                    "Blocked travel to '" + targetDim + "' — missing stages: " + lockedStages);


            List<String> displayNames = new ArrayList<>(lockedStages.size());
            for (StageDefinition stage : lockedStages) {
                displayNames.add(stage.getDisplayName());
            }

            // TODO: investigate what is being sent, how it is handled, and if we can rely on our new deterministic state synchronisation.
            // For example: it might be possible to simply send the dimension ID and then the client can then unpack all the needed stages
            // Since the new system will ensure that client and server data always remain in sync, if we tell the client it can't go to dimension X,
            // it will already have all the data it needs to work out WHY it can't go to X.
            PacketHandler.sendLockFeedbackToPlayer(
                    new LockFeedbackPacket(LockFeedbackPacket.KIND_DIMENSION, displayNames),
                    player
            );
        }
    }
}
