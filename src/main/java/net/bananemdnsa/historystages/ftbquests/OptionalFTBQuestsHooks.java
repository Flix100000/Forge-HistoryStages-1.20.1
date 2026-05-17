package net.bananemdnsa.historystages.ftbquests;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class OptionalFTBQuestsHooks {
    private static MinecraftServer server;

    private OptionalFTBQuestsHooks() {
    }

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    public static void clearServer(MinecraftServer minecraftServer) {
        if (server == minecraftServer) {
            server = null;
        }
    }

    public static void globalUnlocked(String stageId) {
        invoke("onStageUnlocked", new Class<?>[]{String.class}, stageId);
    }

    public static void individualUnlocked(String stageId, UUID playerId) {
        if (server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            individualUnlocked(stageId, player);
        }
    }

    public static void individualUnlocked(String stageId, ServerPlayer player) {
        invoke("onIndividualStageUnlocked", new Class<?>[]{String.class, ServerPlayer.class}, stageId, player);
    }

    private static void invoke(String method, Class<?>[] parameterTypes, Object... args) {
        if (!FabricLoader.getInstance().isModLoaded("ftbquests")) {
            return;
        }

        try {
            Class<?> integration = Class.forName("net.bananemdnsa.historystages.ftbquests.FTBQuestsIntegration");
            integration.getMethod(method, parameterTypes).invoke(null, args);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
