package net.bananemdnsa.historystages.events;

import java.util.UUID;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.HeldAttributeRefresher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forces an attribute re-evaluation when a stage's lock state changes, so the
 * {@link net.bananemdnsa.historystages.mixin.AttributeLockMixin} suppression takes effect
 * immediately on items the player is already holding.
 */
@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AttributeRefreshHandler {

    private AttributeRefreshHandler() {}

    @SubscribeEvent
    public static void onUnlocked(StageEvent.Unlocked event) {
        refreshAllOnline();
    }

    @SubscribeEvent
    public static void onLocked(StageEvent.Locked event) {
        refreshAllOnline();
    }

    @SubscribeEvent
    public static void onIndividualUnlocked(StageEvent.IndividualUnlocked event) {
        refreshPlayer(event.getPlayerUUID());
    }

    @SubscribeEvent
    public static void onIndividualLocked(StageEvent.IndividualLocked event) {
        refreshPlayer(event.getPlayerUUID());
    }

    private static void refreshAllOnline() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HeldAttributeRefresher.refresh(player);
        }
    }

    private static void refreshPlayer(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) HeldAttributeRefresher.refresh(player);
    }
}
