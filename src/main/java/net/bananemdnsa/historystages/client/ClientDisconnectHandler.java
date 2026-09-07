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
        // Nothing cleared these until now, so the force-field and the red overlay kept drawing
        // the last server's lock zones into the next world, until some sync happened to replace
        // them. The renderer's face masks are derived from exactly those boxes and go with them.
        LockBorderClientCache.clear();
        LockBorderRenderer.forgetMasks();
        // Container ids start over on the next server, so a leftover notice could land on the
        // first merchant window there — and it would name stages from a world we have left.
        TradeLockNotice.clear();
        // Which items merchants deal in is the server's answer, and the next one may differ.
        // Kept, it would narrow the picker to the last world's goods.
        ClientTradeGoods.clear();
        // The server pushed its config values into our specs and never wrote our file, so our own
        // settings are only a memory away. Without this they would stay until the game restarts,
        // and the visual ones are visible the moment the next singleplayer world opens.
        int restored = net.bananemdnsa.historystages.data.config.LocalConfigSnapshot.restore();
        System.out.println("[HistoryStages] Client disconnected — reloaded local stage definitions"
                + (restored > 0 ? " and restored " + restored + " local config values." : "."));
    }
}
