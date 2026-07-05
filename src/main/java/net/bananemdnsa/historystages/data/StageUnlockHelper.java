package net.bananemdnsa.historystages.data;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.events.StageEvent;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;

/**
 * Shared post-unlock side effects so commands and {@code AutoTriggerManager}
 * share one code path. Performs the data mutation, sync packet broadcast,
 * event-bus fire, and config-gated chat/actionbar/sound/toast notifications.
 *
 * <p>Note: command callers may still perform their own command-specific
 * side effects (DebugLogger.runtime, source feedback messages, resource
 * pack reloads). Those stay in the command layer.</p>
 */
public final class StageUnlockHelper {

    private StageUnlockHelper() {}

    /**
     * Unlocks a global stage. No-op if already unlocked.
     * Returns true if the stage was newly unlocked.
     */
    public static boolean unlockGlobal(String stageId, ServerLevel level) {
        StageData data = StageData.get(level);
        if (data.hasStage(stageId)) return false;

        data.addStage(stageId);
        data.setDirty();
        StageData.refreshCache(data.getUnlockedStages());

        StageEntry entry = StageManager.getStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;

        MinecraftForge.EVENT_BUS.post(new StageEvent.Unlocked(stageId, displayName));

        // Sync the unlocked-stages list to all players
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

        // Config-gated chat/actionbar/sound broadcast + toast
        broadcastGlobalUnlock(level.getServer(), stageId, displayName, entry);

        return true;
    }

    /**
     * Unlocks an individual stage for the given player. No-op if already unlocked.
     * Returns true if the stage was newly unlocked.
     */
    public static boolean unlockIndividual(String stageId, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        IndividualStageData data = IndividualStageData.get(level);
        if (data.hasStage(player.getUUID(), stageId)) return false;

        data.addStage(player.getUUID(), stageId);
        data.setDirty();

        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;

        MinecraftForge.EVENT_BUS.post(new StageEvent.IndividualUnlocked(stageId, displayName, player.getUUID()));

        // Sync the unlocked-stages set to the player
        PacketHandler.sendIndividualStagesToPlayer(
                new SyncIndividualStagesPacket(data.getUnlockedStages(player.getUUID())),
                player
        );

        // Config-gated chat/actionbar/sound/toast notification to the player
        notifyIndividualUnlock(player, stageId, displayName, entry);

        return true;
    }

    // --- private notification helpers ---------------------------------------

    private static void broadcastGlobalUnlock(MinecraftServer server, String stageId,
                                              String displayName, StageEntry entry) {
        if (server == null) return;
        if (!Config.COMMON.broadcastChat.get() && !Config.COMMON.useActionbar.get()
                && !Config.COMMON.useSounds.get() && !Config.COMMON.useToasts.get()) return;

        String iconId = (entry != null && !entry.getIcon().isEmpty())
                ? entry.getIcon() : Config.COMMON.defaultStageIcon.get();

        String rawMsg = Config.COMMON.unlockMessageFormat.get();
        String formattedMsg = rawMsg.replace("{stage}", displayName).replace("&", "§");
        Component chatMsg = Component.literal("[HistoryStages] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formattedMsg));

        Component actionMsg = Component.literal("§6New Era: " + displayName + " §aUnlocked!");

        server.getPlayerList().getPlayers().forEach(player -> {
            if (Config.COMMON.broadcastChat.get()) {
                player.sendSystemMessage(chatMsg);
            }
            if (Config.COMMON.useActionbar.get()) {
                player.displayClientMessage(actionMsg, true);
            }
            if (Config.COMMON.useSounds.get()) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });

        if (Config.COMMON.useToasts.get()) {
            PacketHandler.sendToastToAll(new StageUnlockedToastPacket(displayName, iconId));
        }
    }

    private static void notifyIndividualUnlock(ServerPlayer player, String stageId,
                                               String displayName, StageEntry entry) {
        if (Config.COMMON.individualBroadcastChat.get()) {
            String configChat = Config.COMMON.individualUnlockMessageFormat.get();
            String finalChat = configChat.replace("{stage}", displayName)
                    .replace("{player}", player.getName().getString())
                    .replace("&", "§");
            player.sendSystemMessage(
                    Component.literal("[HistoryStages] ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(finalChat))
            );
        }
        if (Config.COMMON.individualUseActionbar.get()) {
            String configChat = Config.COMMON.individualUnlockMessageFormat.get();
            String finalChat = configChat.replace("{stage}", displayName)
                    .replace("{player}", player.getName().getString())
                    .replace("&", "§");
            player.displayClientMessage(Component.literal(finalChat), true);
        }
        if (Config.COMMON.individualUseSounds.get()) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
        }
        if (Config.COMMON.individualUseToasts.get()) {
            String iconId = (entry != null && !entry.getIcon().isEmpty())
                    ? entry.getIcon() : Config.COMMON.defaultStageIcon.get();
            PacketHandler.sendToastToPlayer(
                    new StageUnlockedToastPacket(displayName, iconId),
                    player
            );
        }
    }
}
