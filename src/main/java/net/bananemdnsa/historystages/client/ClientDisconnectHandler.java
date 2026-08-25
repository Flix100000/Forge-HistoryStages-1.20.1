package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientPlayerStageCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Reloads local stage definitions when the client disconnects from a server.
 * This ensures singleplayer still uses the local config files after leaving a multiplayer server.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public class ClientDisconnectHandler {

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        // Reload local stage definitions so singleplayer works correctly after leaving a server
        StageManager.load();
        ClientIndividualStageCache.clear();
        ClientPlayerStageCache.clear();
        // Results are answers about the server just left, and a stage id means something else on
        // the next one. Missing here until 2026-08-23, which is why the stage graph could show a
        // stale requirement list for a whole client session.
        ClientDependencyCache.clear();
        System.out.println("[HistoryStages] Client disconnected — reloaded local stage definitions.");
    }
}
