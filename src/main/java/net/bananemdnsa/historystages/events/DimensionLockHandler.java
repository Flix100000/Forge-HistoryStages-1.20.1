package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class DimensionLockHandler {

    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation targetDim = event.getDimension().location();
        String dimId = targetDim.toString();

        String requiredStageId = StageManager.getStageForDimension(dimId);

        if (requiredStageId != null) {
            if (!StageData.SERVER_CACHE.contains(requiredStageId)) {
                event.setCanceled(true);

                StageEntry stageEntry = StageManager.getStages().get(requiredStageId);
                String stageDisplayName = (stageEntry != null) ? stageEntry.getDisplayName() : requiredStageId;

                if (Config.CLIENT.dimShowChat.get()) {
                    MutableComponent chatMsg = Component.translatable("message.historystages.dimension_locked");
                    if (Config.CLIENT.dimShowStagesInChat.get()) {
                        chatMsg.append(Component.translatable("message.historystages.locked_stage", stageDisplayName));
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
}
