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
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DimensionLockHandler {

    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation targetDim = event.getDimension().location();
        String dimId = targetDim.toString();

        String requiredStageId = StageManager.getStageForDimension(dimId);

        if (requiredStageId != null) {
            if (!StageData.SERVER_CACHE.contains(requiredStageId)) {

                // Teleport abbrechen
                event.setCanceled(true);

                // Daten für die Nachricht holen
                StageEntry stageEntry = StageManager.getStages().get(requiredStageId);
                String stageDisplayName = (stageEntry != null) ? stageEntry.getDisplayName() : requiredStageId;

                // 1. Nachricht im Chat
                if (Config.CLIENT.dimShowChat.get()) {
                    // Basis-Nachricht laden
                    MutableComponent chatMsg = Component.translatable("message.historystages.dimension_locked");

                    // Ergänzung des Stage-Namens (Falls aktiv)
                    if (Config.CLIENT.dimShowStagesInChat.get()) {
                        chatMsg.append(Component.translatable("message.historystages.dimension_locked_stage", stageDisplayName));
                    }

                    player.sendSystemMessage(chatMsg);
                }

                // 2. Nachricht in der Actionbar (Mysteriöser Text)
                if (Config.CLIENT.dimUseActionbar.get()) {
                    player.displayClientMessage(
                            Component.translatable("message.historystages.dimension_unknown")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                            true // true = Actionbar
                    );
                }
            }
        }
    }
}