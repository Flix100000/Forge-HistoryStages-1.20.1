package net.bananemdnsa.historystages.util;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the running {@link MinecraftServer} so static utility code can access players
 * without threading a server reference through every call site. Set on
 * {@code SERVER_STARTED}, cleared on {@code SERVER_STOPPING}.
 */
public final class ServerHolder {
    @Nullable
    private static volatile MinecraftServer server;

    private ServerHolder() {
    }

    public static void set(@Nullable MinecraftServer s) {
        server = s;
    }

    @Nullable
    public static MinecraftServer get() {
        return server;
    }
}
