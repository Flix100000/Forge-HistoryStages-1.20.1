package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class DimensionGuard {

    private static final Map<UUID, Long> messageCooldowns = new HashMap<>();
    private static final long COOLDOWN_MILLIS = 3000;

    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation targetDim = event.getDimension().location();
        String dimId = targetDim.toString();

        // 1. Sammle alle Stages, die diese Dimension sperren und dem Spieler fehlen
        List<String> missingStages = StageManager.getStages().entrySet().stream()
                .filter(entry -> entry.getValue().getDimensions().contains(dimId))
                .filter(entry -> !StageData.SERVER_CACHE.contains(entry.getKey()))
                .map(entry -> entry.getValue().getDisplayName())
                .collect(Collectors.toList());

        if (!missingStages.isEmpty()) {
            event.setCanceled(true);

            long currentTime = System.currentTimeMillis();
            long lastMessage = messageCooldowns.getOrDefault(player.getUUID(), 0L);

            if (currentTime - lastMessage > COOLDOWN_MILLIS) {

                // 2. ACTIONBAR (Nur die Info, dass es gesperrt ist)
                if (Config.CLIENT.dimUseActionbar.get()) {
                    player.displayClientMessage(Component.literal("This dimension is currently locked!")
                            .withStyle(ChatFormatting.RED), true);
                }

                // 3. CHAT (Ausführliche Info mit Stages, falls konfiguriert)
                if (Config.CLIENT.dimShowChat.get()) {
                    player.sendSystemMessage(Component.literal("[History] ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("Dimension access denied!")
                                    .withStyle(ChatFormatting.RED)));

                    if (Config.CLIENT.dimShowStagesInChat.get()) {
                        String stageList = String.join(", ", missingStages);
                        player.sendSystemMessage(Component.literal("Required Knowledge: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(stageList).withStyle(ChatFormatting.AQUA)));
                    }
                }

                messageCooldowns.put(player.getUUID(), currentTime);
            }
        }
    }
}