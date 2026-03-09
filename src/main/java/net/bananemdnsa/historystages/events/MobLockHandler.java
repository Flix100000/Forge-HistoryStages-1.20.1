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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    @SubscribeEvent
    public static void onMobAttacked(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (entityType == null) return;

        String entityId = entityType.toString();
        List<String> requiredStageIds = StageManager.getAllStagesForEntity(entityId);
        if (requiredStageIds.isEmpty()) return;

        // Alle Stages sammeln, die noch nicht freigeschaltet sind
        List<String> lockedStages = new ArrayList<>();
        for (String stageId : requiredStageIds) {
            if (!StageData.SERVER_CACHE.contains(stageId)) {
                lockedStages.add(stageId);
            }
        }

        if (!lockedStages.isEmpty()) {
            event.setCanceled(true);

            long now = System.currentTimeMillis();
            Long lastMessage = MESSAGE_COOLDOWNS.get(player.getUUID());
            if (lastMessage != null && (now - lastMessage) < COOLDOWN_MS) return;
            MESSAGE_COOLDOWNS.put(player.getUUID(), now);

            if (Config.CLIENT.mobShowChat.get()) {
                MutableComponent chatMsg = Component.translatable("message.historystages.mob_locked");
                if (Config.CLIENT.mobShowStagesInChat.get()) {
                    for (String stageId : lockedStages) {
                        StageEntry stageEntry = StageManager.getStages().get(stageId);
                        String displayName = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
                        chatMsg.append(Component.translatable("message.historystages.locked_stage", displayName));
                    }
                }
                player.sendSystemMessage(chatMsg);
            }

            if (Config.CLIENT.mobUseActionbar.get()) {
                player.displayClientMessage(
                        Component.translatable("message.historystages.mob_unknown")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                        true
                );
            }
        }
    }
}
