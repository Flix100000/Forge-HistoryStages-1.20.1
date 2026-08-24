package net.bananemdnsa.historystages.api.stage;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;

/**
 * Reading and setting stage state — the named place the parent design asks for in §6.3.
 *
 * <p>Everything that unlocks or relocks a stage comes through here: the editor, the research
 * pedestal, {@code /history stage unlock}, an auto-trigger, an FTB Quests reward, and an addon
 * granting a stage of its own. That is not tidiness. Each of these four methods also writes the
 * SavedData, broadcasts the sync packet, clears the structure and biome caches, reloads recipes
 * where the scope calls for it, fires the matching {@link StageEvent}, and sends the player the
 * sound, the chat line and the toast — all of it config-gated.
 *
 * <p>A caller that reproduces an unlock by hand gets a stage that is technically unlocked and
 * visibly broken. That is not hypothetical: {@code ToggleStageLockPacket} had its own copy, the
 * two drifted, and unlocking at a pedestal stopped granting recipes. Commit {@code fcad6ea}
 * repaired it by deleting the copy.
 *
 * <p>Static rather than an interface: there is exactly one implementation and nowhere to inject
 * another, and static entry points are the familiar shape in Minecraft modding. Command callers
 * keep their own command-specific extras — source feedback, debug logging, resource-pack reloads
 * — in the command layer.
 *
 * <p>One of two places under {@code api/} that names NeoForge, and deliberately: firing
 * {@link StageEvent} onto the bus <em>is</em> the loader bridge. See the Phase 9 design §5.
 */
public final class StageStates {

    private StageStates() {}

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

        NeoForge.EVENT_BUS.post(new StageEvent.Unlocked(stageId, displayName));

        // Sync the unlocked-stages list to all players
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

        // Config-gated chat/actionbar/sound broadcast + toast
        broadcastGlobalUnlock(level.getServer(), stageId, displayName, entry);

        invalidateLockCaches();
        MinecraftServer unlockServer = level.getServer();
        if (unlockServer != null) {
            // Recipes gate globally only, so this belongs on the global paths and nowhere else.
            // Without it a stage unlocked at a pedestal grants its recipes to nobody until
            // something else reloads them — which was true of every path except the editor's.
            PacketHandler.reloadRecipesOnly(unlockServer);
        }

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

        NeoForge.EVENT_BUS.post(new StageEvent.IndividualUnlocked(stageId, displayName, player.getUUID()));

        // Sync the unlocked-stages set to the player
        PacketHandler.sendIndividualStagesToPlayer(
                new SyncIndividualStagesPacket(data.getUnlockedStages(player.getUUID())),
                player
        );

        // Config-gated chat/actionbar/sound/toast notification to the player
        notifyIndividualUnlock(player, stageId, displayName, entry);

        // Structures and biomes can be gated per player, so their caches go stale here too.
        // Recipes cannot — that category is global-only — so no reload belongs on this path.
        invalidateLockCaches();

        return true;
    }

    /**
     * Re-locks a global stage (used by temporary-mode timer expiry). No-op if
     * already locked. Mirrors the {@code /stage lock} command's side effects:
     * removes the stage, refreshes the cache, syncs to all players, fires the
     * {@link StageEvent.Locked} event, and reloads resources so recipe / JEI
     * gating updates. Returns true if the stage was newly locked.
     */
    public static boolean relockGlobal(String stageId, ServerLevel level) {
        StageData data = StageData.get(level);
        if (!data.hasStage(stageId)) return false;

        data.removeStage(stageId);
        data.setDirty();
        StageData.refreshCache(data.getUnlockedStages());

        StageEntry entry = StageManager.getStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;

        NeoForge.EVENT_BUS.post(new StageEvent.Locked(stageId, displayName));

        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

        // Config-gated chat/actionbar/sound broadcast — same "locked" feedback the
        // /stage lock command produces.
        broadcastGlobalLock(level.getServer(), displayName);

        invalidateLockCaches();

        MinecraftServer server = level.getServer();
        if (server != null) {
            // The same recipe-only reload as unlocking, which it was not until 2026-08-24: a full
            // reloadResources had stood here since the temporary-stage mode was added, and it
            // stutters visibly on a large pack.
            //
            // Nothing this mod does needs the rest of it. Recipes are not removed from the game
            // when a stage locks — they are asked about at craft and display time — so the reload
            // exists to resend the list to clients, which reloadRecipesOnly does. And the mod
            // registers no reload listener at all: no AddReloadListenerEvent, no TagsUpdatedEvent,
            // no OnDatapackSyncEvent. Tags, advancements and loot tables were being rebuilt for
            // nobody.
            PacketHandler.reloadRecipesOnly(server);
        }
        return true;
    }

    /**
     * Re-locks an individual stage for the given player (used by temporary-mode
     * timer expiry). No-op if already locked. Removes the stage, syncs to the
     * player, fires {@link StageEvent.IndividualLocked}, and drops any
     * now-locked items from the player's inventory. Returns true if newly locked.
     */
    public static boolean relockIndividual(String stageId, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        IndividualStageData data = IndividualStageData.get(level);
        if (!data.hasStage(player.getUUID(), stageId)) return false;

        data.removeStage(player.getUUID(), stageId);
        data.setDirty();

        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;

        NeoForge.EVENT_BUS.post(new StageEvent.IndividualLocked(stageId, displayName, player.getUUID()));

        PacketHandler.sendIndividualStagesToPlayer(
                new SyncIndividualStagesPacket(data.getUnlockedStages(player.getUUID())),
                player
        );

        net.bananemdnsa.historystages.util.lock.StageLockHelper.dropLockedItemsForPlayer(player, stageId);

        // Config-gated "locked" feedback to the affected player.
        notifyIndividualLock(player, displayName);

        invalidateLockCaches();
        return true;
    }

    /**
     * Drops the structure and biome lock caches so the next tick recomputes them.
     *
     * <p>Both are keyed off which stages are unlocked, and neither notices on its own. Without
     * this the force field around a locked structure stays up until the next chunk scan, which
     * can be minutes — and it used to happen on every path but the editor's, because the editor
     * had these two calls written into its packet handler instead of here.
     *
     * <p>{@code StructureGenerationGate.rebuild()} is deliberately absent: {@code StageData}
     * already calls it from {@code addStage} and {@code removeStage}, so it is covered whichever
     * way the stage moved.
     */
    private static void invalidateLockCaches() {
        net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
        net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
    }

    // --- private notification helpers ---------------------------------------

    private static void broadcastGlobalLock(MinecraftServer server, String displayName) {
        if (server == null) return;
        if (!Config.COMMON.broadcastChat.get() && !Config.COMMON.useActionbar.get()
                && !Config.COMMON.useSounds.get()) return;

        Component chatMsg = Component.literal("[HistoryStages] ")
                .withStyle(ChatFormatting.RED)
                .append(Component.translatable("message.historystages.stage_forgotten", displayName)
                        .withStyle(ChatFormatting.WHITE));
        Component actionMsg = Component.translatable("message.historystages.stage_locked_action", displayName);

        server.getPlayerList().getPlayers().forEach(player -> {
            if (Config.COMMON.broadcastChat.get()) {
                player.sendSystemMessage(chatMsg);
            }
            if (Config.COMMON.useActionbar.get()) {
                player.displayClientMessage(actionMsg, true);
            }
            if (Config.COMMON.useSounds.get()) {
                player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });
    }

    private static void notifyIndividualLock(ServerPlayer player, String displayName) {
        if (Config.COMMON.individualBroadcastChat.get()) {
            player.sendSystemMessage(Component.literal("[HistoryStages] ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.translatable("message.historystages.stage_forgotten", displayName)
                            .withStyle(ChatFormatting.WHITE)));
        }
        if (Config.COMMON.individualUseActionbar.get()) {
            player.displayClientMessage(Component.translatable("message.historystages.stage_locked_action", displayName), true);
        }
        if (Config.COMMON.individualUseSounds.get()) {
            player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.75F, 1.0F);
        }
    }

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

        Component actionMsg = Component.translatable("message.historystages.new_era_unlocked", displayName);

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
