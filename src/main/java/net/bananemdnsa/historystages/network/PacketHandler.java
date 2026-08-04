package net.bananemdnsa.historystages.network;
import net.bananemdnsa.historystages.network.serverbound.RequestClusterShapesPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleStructureVizPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStructureDebugPacket;
import net.bananemdnsa.historystages.network.serverbound.DepositDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.CheckDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStageDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveConfigPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleStageLockPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleIndividualStageLockPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteStagePacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStagePacket;
import net.bananemdnsa.historystages.network.serverbound.CreateFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.RenameFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.MoveStagesPacket;
import net.bananemdnsa.historystages.network.serverbound.MoveFoldersPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestTemporaryCountsPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestIndividualStatesPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestEditorDataPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphPositionsPacket;
import net.bananemdnsa.historystages.network.serverbound.RearrangeGraphPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphInfoPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncLockBordersPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.LockFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStructureRegistryPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncDependencyStatusPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncTemporaryCountsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStatesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncGraphConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorSyncPacket;
import net.bananemdnsa.historystages.network.clientbound.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;

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
        registrar.playToClient(SyncGraphConfigPacket.TYPE, SyncGraphConfigPacket.STREAM_CODEC, SyncGraphConfigPacket::handle);
        registrar.playToClient(SyncIndividualStagesPacket.TYPE, SyncIndividualStagesPacket.STREAM_CODEC, SyncIndividualStagesPacket::handle);
        registrar.playToClient(SyncTemporaryCountsPacket.TYPE, SyncTemporaryCountsPacket.STREAM_CODEC, SyncTemporaryCountsPacket::handle);
        registrar.playToClient(SyncIndividualStatesPacket.TYPE, SyncIndividualStatesPacket.STREAM_CODEC, SyncIndividualStatesPacket::handle);

        // Client → Server
        registrar.playToServer(RequestEditorDataPacket.TYPE, RequestEditorDataPacket.STREAM_CODEC, RequestEditorDataPacket::handle);
        registrar.playToServer(RequestTemporaryCountsPacket.TYPE, RequestTemporaryCountsPacket.STREAM_CODEC, RequestTemporaryCountsPacket::handle);
        registrar.playToServer(RequestIndividualStatesPacket.TYPE, RequestIndividualStatesPacket.STREAM_CODEC, RequestIndividualStatesPacket::handle);
        registrar.playToServer(SaveStagePacket.TYPE, SaveStagePacket.STREAM_CODEC, SaveStagePacket::handle);
        registrar.playToServer(DeleteStagePacket.TYPE, DeleteStagePacket.STREAM_CODEC, DeleteStagePacket::handle);
        registrar.playToServer(CreateFolderPacket.TYPE, CreateFolderPacket.STREAM_CODEC, CreateFolderPacket::handle);
        registrar.playToServer(RenameFolderPacket.TYPE, RenameFolderPacket.STREAM_CODEC, RenameFolderPacket::handle);
        registrar.playToServer(DeleteFolderPacket.TYPE, DeleteFolderPacket.STREAM_CODEC, DeleteFolderPacket::handle);
        registrar.playToServer(MoveStagesPacket.TYPE, MoveStagesPacket.STREAM_CODEC, MoveStagesPacket::handle);
        registrar.playToServer(MoveFoldersPacket.TYPE, MoveFoldersPacket.STREAM_CODEC, MoveFoldersPacket::handle);
        registrar.playToServer(ToggleStageLockPacket.TYPE, ToggleStageLockPacket.STREAM_CODEC, ToggleStageLockPacket::handle);
        registrar.playToServer(ToggleIndividualStageLockPacket.TYPE, ToggleIndividualStageLockPacket.STREAM_CODEC, ToggleIndividualStageLockPacket::handle);
        registrar.playToServer(SaveConfigPacket.TYPE, SaveConfigPacket.STREAM_CODEC, SaveConfigPacket::handle);
        registrar.playToServer(CheckDependencyPacket.TYPE, CheckDependencyPacket.STREAM_CODEC, CheckDependencyPacket::handle);
        registrar.playToServer(RequestStageDependencyPacket.TYPE, RequestStageDependencyPacket.STREAM_CODEC, RequestStageDependencyPacket::handle);
        registrar.playToServer(DepositDependencyPacket.TYPE, DepositDependencyPacket.STREAM_CODEC, DepositDependencyPacket::handle);
        registrar.playToServer(RequestStructureDebugPacket.TYPE, RequestStructureDebugPacket.STREAM_CODEC, RequestStructureDebugPacket::handle);
        registrar.playToServer(ToggleStructureVizPacket.TYPE, ToggleStructureVizPacket.STREAM_CODEC, ToggleStructureVizPacket::handle);
        registrar.playToServer(RequestClusterShapesPacket.TYPE, RequestClusterShapesPacket.STREAM_CODEC, RequestClusterShapesPacket::handle);
        registrar.playToServer(SaveGraphPositionsPacket.TYPE, SaveGraphPositionsPacket.STREAM_CODEC, SaveGraphPositionsPacket::handle);
        registrar.playToServer(RearrangeGraphPacket.TYPE, RearrangeGraphPacket.STREAM_CODEC, RearrangeGraphPacket::handle);
        registrar.playToServer(SaveStageGraphInfoPacket.TYPE, SaveStageGraphInfoPacket.STREAM_CODEC, SaveStageGraphInfoPacket::handle);

        // Dependency sync (Server → Client)
        registrar.playToClient(SyncDependencyStatusPacket.TYPE, SyncDependencyStatusPacket.STREAM_CODEC, SyncDependencyStatusPacket::handle);

        // Structure registry sync (Server → Client)
        registrar.playToClient(SyncStructureRegistryPacket.TYPE, SyncStructureRegistryPacket.STREAM_CODEC, SyncStructureRegistryPacket::handle);

        // Lock feedback (Server → Client) — client reads its own CLIENT config to decide display
        registrar.playToClient(LockFeedbackPacket.TYPE, LockFeedbackPacket.STREAM_CODEC, LockFeedbackPacket::handle);

        // Editor feedback (Server → Client) — toast notifications for editor actions
        registrar.playToClient(EditorFeedbackPacket.TYPE, EditorFeedbackPacket.STREAM_CODEC, EditorFeedbackPacket::handle);

        // Lock border sync (Server → Client) — drives the force-field overlay near locked structures
        registrar.playToClient(SyncLockBordersPacket.TYPE, SyncLockBordersPacket.STREAM_CODEC, SyncLockBordersPacket::handle);
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

    public static void sendToastToPlayer(StageUnlockedToastPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
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

    public static void sendGraphConfigToPlayer(SyncGraphConfigPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendGraphConfigToAll(SyncGraphConfigPacket packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendIndividualStagesToPlayer(SyncIndividualStagesPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendStructureRegistryToPlayer(SyncStructureRegistryPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    // Send lock feedback (dimension or mob) to a specific player — client decides display
    public static void sendLockFeedbackToPlayer(LockFeedbackPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendLockBordersToPlayer(SyncLockBordersPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendEditorFeedback(EditorFeedbackPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
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
