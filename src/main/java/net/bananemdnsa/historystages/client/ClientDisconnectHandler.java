package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reloads local stage definitions when the client disconnects from a server.
 * This ensures singleplayer still uses the local config files after leaving a multiplayer server.
 */
@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public class ClientDisconnectHandler {

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        // Reload local stage definitions so singleplayer works correctly after leaving a server
        StageManager.load();
        System.out.println("[HistoryStages] Client disconnected — reloaded local stage definitions.");
    }
}
