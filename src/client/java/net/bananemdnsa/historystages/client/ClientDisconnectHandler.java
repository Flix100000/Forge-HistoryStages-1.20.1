package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class ClientDisconnectHandler {
    private ClientDisconnectHandler() {
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientStageCache.clear();
            ClientIndividualStageCache.clear();
            ClientDependencyCache.clear();
            ClientStructureRegistry.clear();
        });
    }
}
