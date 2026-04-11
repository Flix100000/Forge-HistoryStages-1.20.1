package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class MobLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    @SubscribeEvent
    public static void onMobAttacked(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (entityType == null) return;

        String entityId = entityType.toString();

        // Check global stages
        List<String> lockedStages = new ArrayList<>();
        List<String> requiredStageIds = StageManager.getAllStagesForAttackLockedEntity(entityId);
        for (String stageId : requiredStageIds) {
            if (!StageData.SERVER_CACHE.contains(stageId)) {
                lockedStages.add(stageId);
            }
        }

        // Check individual stages
        List<String> individualStageIds = StageManager.getAllIndividualStagesForAttackLockedEntity(entityId);
        if (!individualStageIds.isEmpty()) {
            java.util.Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(player.getUUID(), java.util.Collections.emptySet());
            for (String stageId : individualStageIds) {
                if (!playerStages.contains(stageId)) {
                    lockedStages.add(stageId);
                }
            }
        }

        if (lockedStages.isEmpty() && requiredStageIds.isEmpty() && individualStageIds.isEmpty()) return;

        if (!lockedStages.isEmpty()) {
            event.setCanceled(true);
            DebugLogger.runtimeThrottled("Mob Attack Lock", "mob_attack_" + player.getUUID() + "_" + entityId,
                    "<" + player.getName().getString() + "> Attack on '" + entityId + "' blocked — missing stages: " + lockedStages);

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
