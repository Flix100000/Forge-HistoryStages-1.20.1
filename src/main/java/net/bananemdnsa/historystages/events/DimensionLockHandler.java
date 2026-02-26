package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

                // --- Berücksichtigung der Client-Configs ---

                // 1. Nachricht im Chat (Falls dimShowChat aktiv ist)
                if (Config.CLIENT.dimShowChat.get()) {
                    String message = "§cYou haven't reached the required era to enter this dimension!";

                    // Ergänzung des Stage-Namens im Chat (Falls dimShowStagesInChat aktiv ist)
                    if (Config.CLIENT.dimShowStagesInChat.get()) {
                        message += " §8(Required: §e" + stageDisplayName + "§8)";
                    }

                    player.sendSystemMessage(Component.literal(message));
                }

                // 2. Nachricht in der Actionbar (NEU: Mysteriöser Text statt Stage-Name)
                if (Config.CLIENT.dimUseActionbar.get()) {
                    player.displayClientMessage(
                            Component.literal("??? This dimension is still unknown to you ???")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                            true // true = Actionbar
                    );
                }

            }
        }
    }
}