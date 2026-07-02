package net.bananemdnsa.historystages.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

/**
 * Only called when no server is running in this JVM (i.e. a multiplayer
 * client connected to a remote server) — the integrated-server case is
 * handled by going through {@link net.neoforged.neoforge.server.ServerLifecycleHooks}
 * instead, since that is safe to read from any thread.
 */
public final class ClientRegistryAccessHelper {

    private ClientRegistryAccessHelper() {}

    public static HolderLookup.Provider get() {
        var level = Minecraft.getInstance().level;
        return level != null ? level.registryAccess() : null;
    }
}
