package net.bananemdnsa.historystages.compat.kubejs;

import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Turns the mod's own {@link StageEvent} into posts on the KubeJS event group. Registered from
 * the mod class behind a {@code ModList.isLoaded} check, so it exists only alongside KubeJS.
 *
 * <p>Listening to {@link StageEvent} rather than hooking the unlock paths is what makes this
 * complete: the event fires from the editor, the research pedestal, the command, an auto-trigger
 * and an FTB Quests reward alike, because all of them go through {@code StageStates}.
 */
public final class StageEventForwarder {

    private StageEventForwarder() {}

    public static void register(IEventBus bus) {
        bus.addListener(StageEvent.Unlocked.class, event ->
                HistoryStagesKubeEvents.UNLOCKED.post(
                        new HistoryStagesKubeEvents.StageKubeEvent(
                                event.getStageId(), event.getDisplayName()),
                        event.getStageId()));

        bus.addListener(StageEvent.Locked.class, event ->
                HistoryStagesKubeEvents.LOCKED.post(
                        new HistoryStagesKubeEvents.StageKubeEvent(
                                event.getStageId(), event.getDisplayName()),
                        event.getStageId()));

        bus.addListener(StageEvent.IndividualUnlocked.class, event ->
                HistoryStagesKubeEvents.INDIVIDUAL_UNLOCKED.post(
                        new HistoryStagesKubeEvents.IndividualStageKubeEvent(
                                event.getStageId(), event.getDisplayName(),
                                playerOf(event.getPlayerUUID())),
                        event.getStageId()));

        bus.addListener(StageEvent.IndividualLocked.class, event ->
                HistoryStagesKubeEvents.INDIVIDUAL_LOCKED.post(
                        new HistoryStagesKubeEvents.IndividualStageKubeEvent(
                                event.getStageId(), event.getDisplayName(),
                                playerOf(event.getPlayerUUID())),
                        event.getStageId()));
    }

    /**
     * The event carries a UUID, not a player. Scripts want the player —
     * {@code event.player.tell(…)} is the first thing anyone writes — so it is resolved once here
     * instead of in every script. Null when that player is offline, which an individual relock on
     * a timer can perfectly well be.
     */
    private static ServerPlayer playerOf(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }
}
