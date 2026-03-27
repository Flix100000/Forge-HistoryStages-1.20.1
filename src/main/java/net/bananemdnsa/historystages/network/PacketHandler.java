package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.concurrent.CompletableFuture;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HistoryStages.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, SyncStagesPacket.class, SyncStagesPacket::encode, SyncStagesPacket::decode, SyncStagesPacket::handle);
        INSTANCE.registerMessage(id++, StageUnlockedToastPacket.class, StageUnlockedToastPacket::encode, StageUnlockedToastPacket::decode, StageUnlockedToastPacket::handle);
        INSTANCE.registerMessage(id++, RequestEditorDataPacket.class, RequestEditorDataPacket::encode, RequestEditorDataPacket::decode, RequestEditorDataPacket::handle);
        INSTANCE.registerMessage(id++, EditorSyncPacket.class, EditorSyncPacket::encode, EditorSyncPacket::decode, EditorSyncPacket::handle);
        INSTANCE.registerMessage(id++, SaveStagePacket.class, SaveStagePacket::encode, SaveStagePacket::decode, SaveStagePacket::handle);
        INSTANCE.registerMessage(id++, DeleteStagePacket.class, DeleteStagePacket::encode, DeleteStagePacket::decode, DeleteStagePacket::handle);
        INSTANCE.registerMessage(id++, ToggleStageLockPacket.class, ToggleStageLockPacket::encode, ToggleStageLockPacket::decode, ToggleStageLockPacket::handle);
        INSTANCE.registerMessage(id++, SaveConfigPacket.class, SaveConfigPacket::encode, SaveConfigPacket::decode, SaveConfigPacket::handle);
        INSTANCE.registerMessage(id++, SyncStageDefinitionsPacket.class, SyncStageDefinitionsPacket::encode, SyncStageDefinitionsPacket::decode, SyncStageDefinitionsPacket::handle);
    }

    // Hilfsmethode, um das Paket an alle Spieler zu senden
    public static void sendToAll(SyncStagesPacket packet) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }

    // Hilfsmethode, um das Paket an einen bestimmten Spieler zu senden (z.B. beim Login)
    public static void sendToPlayer(SyncStagesPacket packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    // Toast-Benachrichtigung an alle Spieler senden
    public static void sendToastToAll(StageUnlockedToastPacket packet) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }

    // Send stage definitions to a specific player (e.g. on login)
    public static void sendDefinitionsToPlayer(SyncStageDefinitionsPacket packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    // Send stage definitions to all players (e.g. after editor save/delete)
    public static void sendDefinitionsToAll(SyncStageDefinitionsPacket packet) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }

    /**
     * Targeted recipe-only reload — re-reads recipe JSONs from datapacks and re-applies them.
     * Much lighter than server.reloadResources() which reloads ALL datapacks (tags, advancements, etc.).
     * Runs async: prepare phase on background thread, apply phase on server thread.
     * After apply, syncs updated recipes to all clients.
     */
    public static void reloadRecipesOnly(MinecraftServer server) {
        server.getRecipeManager().reload(
                CompletableFuture::completedFuture,
                server.getResourceManager(),
                InactiveProfiler.INSTANCE,
                InactiveProfiler.INSTANCE,
                Util.backgroundExecutor(),
                server
        ).thenRunAsync(() -> resyncRecipes(server), server)
         .exceptionally(e -> {
             System.err.println("[HistoryStages] Recipe reload failed: " + e.getMessage());
             return null;
         });
    }

    // Resync recipes to all players (client-side update)
    private static void resyncRecipes(MinecraftServer server) {
        ClientboundUpdateRecipesPacket recipePacket = new ClientboundUpdateRecipesPacket(
                server.getRecipeManager().getRecipes());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(recipePacket);
        }
    }

    // Send a packet from client to server
    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }
}