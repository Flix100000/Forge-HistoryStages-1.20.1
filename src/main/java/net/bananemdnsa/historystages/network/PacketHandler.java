package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.Util;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class PacketHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Server → Client
        registrar.playToClient(SyncStagesPacket.TYPE, SyncStagesPacket.STREAM_CODEC, SyncStagesPacket::handle);
        registrar.playToClient(StageUnlockedToastPacket.TYPE, StageUnlockedToastPacket.STREAM_CODEC, StageUnlockedToastPacket::handle);
        registrar.playToClient(EditorSyncPacket.TYPE, EditorSyncPacket.STREAM_CODEC, EditorSyncPacket::handle);
        registrar.playToClient(SyncStageDefinitionsPacket.TYPE, SyncStageDefinitionsPacket.STREAM_CODEC, SyncStageDefinitionsPacket::handle);
        registrar.playToClient(SyncConfigPacket.TYPE, SyncConfigPacket.STREAM_CODEC, SyncConfigPacket::handle);

        // Client → Server
        registrar.playToServer(RequestEditorDataPacket.TYPE, RequestEditorDataPacket.STREAM_CODEC, RequestEditorDataPacket::handle);
        registrar.playToServer(SaveStagePacket.TYPE, SaveStagePacket.STREAM_CODEC, SaveStagePacket::handle);
        registrar.playToServer(DeleteStagePacket.TYPE, DeleteStagePacket.STREAM_CODEC, DeleteStagePacket::handle);
        registrar.playToServer(ToggleStageLockPacket.TYPE, ToggleStageLockPacket.STREAM_CODEC, ToggleStageLockPacket::handle);
        registrar.playToServer(SaveConfigPacket.TYPE, SaveConfigPacket.STREAM_CODEC, SaveConfigPacket::handle);
    }

    public static void sendToAll(SyncStagesPacket packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendToPlayer(SyncStagesPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToastToAll(StageUnlockedToastPacket packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendDefinitionsToPlayer(SyncStageDefinitionsPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendDefinitionsToAll(SyncStageDefinitionsPacket packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendConfigToPlayer(SyncConfigPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendConfigToAll(SyncConfigPacket packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendToServer(Object packet) {
        PacketDistributor.sendToServer((CustomPacketPayload) packet);
    }

    /**
     * Targeted recipe-only reload — re-reads recipe JSONs from datapacks and re-applies them.
     * Much lighter than server.reloadResources() which reloads ALL datapacks.
     * Runs async: prepare phase on background thread, apply phase on server thread.
     * After apply, syncs updated recipes to all clients.
     */
    public static void reloadRecipesOnly(MinecraftServer server) {
        server.getRecipeManager().reload(
                CompletableFuture::completedFuture,
                server.getResourceManager(),
                net.minecraft.util.profiling.InactiveProfiler.INSTANCE,
                net.minecraft.util.profiling.InactiveProfiler.INSTANCE,
                Util.backgroundExecutor(),
                server
        ).thenRunAsync(() -> resyncRecipes(server), server)
         .exceptionally(e -> {
             System.err.println("[HistoryStages] Recipe reload failed: " + e.getMessage());
             return null;
         });
    }

    private static void resyncRecipes(MinecraftServer server) {
        ClientboundUpdateRecipesPacket recipePacket = new ClientboundUpdateRecipesPacket(
                server.getRecipeManager().getRecipes());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(recipePacket);
        }
    }
}
