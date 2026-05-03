package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;

public final class Networking {
    private Networking() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(StageDefinitionsPayload.TYPE, StageDefinitionsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(UnlockedStagesPayload.TYPE, UnlockedStagesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(UnlockedIndividualStagesPayload.TYPE, UnlockedIndividualStagesPayload.STREAM_CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new StageDefinitionsPayload(
                StageManager.serializeStages(StageManager.getStages()),
                StageManager.serializeStages(StageManager.getIndividualStages())
        ));
        ServerPlayNetworking.send(player, new UnlockedStagesPayload(
                StageData.get(player.serverLevel()).getUnlockedStages()
        ));
        ServerPlayNetworking.send(player, new UnlockedIndividualStagesPayload(
                new java.util.ArrayList<>(IndividualStageData.get(player.serverLevel()).getUnlockedStages(player.getUUID()))
        ));
    }
}
