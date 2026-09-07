package net.bananemdnsa.historystages.network;
import net.bananemdnsa.historystages.network.serverbound.RequestClusterShapesPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleStructureVizPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStructureDebugPacket;
import net.bananemdnsa.historystages.network.serverbound.DepositDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.CheckDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStageDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveConfigPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphConfigPacket;
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
import net.bananemdnsa.historystages.network.serverbound.RequestTradeGoodsPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphPositionsPacket;
import net.bananemdnsa.historystages.network.serverbound.RearrangeGraphPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphInfoPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphStylePacket;
import net.bananemdnsa.historystages.network.serverbound.PedestalControlPacket;
import net.bananemdnsa.historystages.network.serverbound.TakeLecternScrollPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncLockBordersPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.LockFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncTradeGoodsPacket;
import net.bananemdnsa.historystages.network.clientbound.TradeLockedPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStructureRegistryPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncDependencyStatusPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncTemporaryCountsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStatesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncGraphConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncVisualConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorSyncPacket;
import net.bananemdnsa.historystages.network.clientbound.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.OpenLecternScrollPacket;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
        registrar.playToClient(SyncVisualConfigPacket.TYPE, SyncVisualConfigPacket.STREAM_CODEC, SyncVisualConfigPacket::handle);
        registrar.playToClient(SyncIndividualStagesPacket.TYPE, SyncIndividualStagesPacket.STREAM_CODEC, SyncIndividualStagesPacket::handle);
        registrar.playToClient(SyncTemporaryCountsPacket.TYPE, SyncTemporaryCountsPacket.STREAM_CODEC, SyncTemporaryCountsPacket::handle);
        registrar.playToClient(SyncIndividualStatesPacket.TYPE, SyncIndividualStatesPacket.STREAM_CODEC, SyncIndividualStatesPacket::handle);
        registrar.playToClient(OpenLecternScrollPacket.TYPE, OpenLecternScrollPacket.STREAM_CODEC, OpenLecternScrollPacket::handle);

        // Client → Server
        registrar.playToServer(RequestEditorDataPacket.TYPE, RequestEditorDataPacket.STREAM_CODEC, RequestEditorDataPacket::handle);
        registrar.playToServer(RequestTradeGoodsPacket.TYPE, RequestTradeGoodsPacket.STREAM_CODEC, RequestTradeGoodsPacket::handle);
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
        registrar.playToServer(SaveGraphConfigPacket.TYPE, SaveGraphConfigPacket.STREAM_CODEC, SaveGraphConfigPacket::handle);
        registrar.playToServer(CheckDependencyPacket.TYPE, CheckDependencyPacket.STREAM_CODEC, CheckDependencyPacket::handle);
        registrar.playToServer(RequestStageDependencyPacket.TYPE, RequestStageDependencyPacket.STREAM_CODEC, RequestStageDependencyPacket::handle);
        registrar.playToServer(DepositDependencyPacket.TYPE, DepositDependencyPacket.STREAM_CODEC, DepositDependencyPacket::handle);
        registrar.playToServer(RequestStructureDebugPacket.TYPE, RequestStructureDebugPacket.STREAM_CODEC, RequestStructureDebugPacket::handle);
        registrar.playToServer(ToggleStructureVizPacket.TYPE, ToggleStructureVizPacket.STREAM_CODEC, ToggleStructureVizPacket::handle);
        registrar.playToServer(RequestClusterShapesPacket.TYPE, RequestClusterShapesPacket.STREAM_CODEC, RequestClusterShapesPacket::handle);
        registrar.playToServer(SaveGraphPositionsPacket.TYPE, SaveGraphPositionsPacket.STREAM_CODEC, SaveGraphPositionsPacket::handle);
        registrar.playToServer(RearrangeGraphPacket.TYPE, RearrangeGraphPacket.STREAM_CODEC, RearrangeGraphPacket::handle);
        registrar.playToServer(SaveStageGraphInfoPacket.TYPE, SaveStageGraphInfoPacket.STREAM_CODEC, SaveStageGraphInfoPacket::handle);
        registrar.playToServer(SaveStageGraphStylePacket.TYPE, SaveStageGraphStylePacket.STREAM_CODEC, SaveStageGraphStylePacket::handle);
        registrar.playToServer(PedestalControlPacket.TYPE, PedestalControlPacket.STREAM_CODEC, PedestalControlPacket::handle);
        registrar.playToServer(TakeLecternScrollPacket.TYPE, TakeLecternScrollPacket.STREAM_CODEC, TakeLecternScrollPacket::handle);

        // Dependency sync (Server → Client)
        registrar.playToClient(SyncDependencyStatusPacket.TYPE, SyncDependencyStatusPacket.STREAM_CODEC, SyncDependencyStatusPacket::handle);

        // Structure registry sync (Server → Client)
        registrar.playToClient(SyncStructureRegistryPacket.TYPE, SyncStructureRegistryPacket.STREAM_CODEC, SyncStructureRegistryPacket::handle);

        // Lock feedback (Server → Client) — client reads its own CLIENT config to decide display
        registrar.playToClient(LockFeedbackPacket.TYPE, LockFeedbackPacket.STREAM_CODEC, LockFeedbackPacket::handle);
        registrar.playToClient(TradeLockedPacket.TYPE, TradeLockedPacket.STREAM_CODEC, TradeLockedPacket::handle);
        registrar.playToClient(SyncTradeGoodsPacket.TYPE, SyncTradeGoodsPacket.STREAM_CODEC, SyncTradeGoodsPacket::handle);

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

    public static void sendVisualConfigToPlayer(SyncVisualConfigPacket packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendVisualConfigToAll(SyncVisualConfigPacket packet) {
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

    /** Why the trade window that just opened is empty. Sent right after the window itself. */
    public static void sendTradeLockedToPlayer(TradeLockedPacket packet, ServerPlayer player) {
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
     * Asks for whatever a change to what is gated needs. Carried out by
     * {@link #runRequestedLockReload} at the end of the tick.
     *
     * <p>Only asks, so that unlocking three stages in the same tick — a quest handing out a
     * bundle, an auto-trigger catching up — ends in one piece of work rather than three.
     */
    public static void reloadForLockChange(MinecraftServer server) {
        reloadRequested = true;
    }

    /**
     * Carries out a requested reload, at most once per tick, and picks the cheapest one that will
     * do.
     *
     * <p><strong>Resending is the normal case.</strong> The gate itself needs nothing: it is
     * consulted when a recipe is asked for, so it flips the moment the stage does. What the resend
     * is for is the clients — it is what makes JEI notice, and without it items hidden by a stage
     * never come back after an unlock. Two GameTests hold both halves of that.
     *
     * <p><strong>A full datapack reload only when the set of hidden recipes actually changed.</strong>
     * A machine that takes the whole recipe list and searches it itself — Create's basin, and most
     * modded machines — keeps that list until a datapack reload tells it to let go, so without one
     * it would take a lock correctly and then stay stuck after the unlock. But it only needs to be
     * told when the list would come out different, and {@code VisibleRecipes.gatedSetChanged}
     * answers exactly that by working the set out and comparing it. A stage gating blocks, biomes
     * or mobs costs nothing; so does saving a stage in the editor without changing what it gates.
     *
     * <p>The reason for being this careful: reloading every datapack on a large modpack freezes
     * the server for as long as it takes, and this can be reached from an auto-trigger, which
     * fires while people are playing. That is why commit {@code ca3988f} took the full reload out
     * in the first place — it is back only where nothing else will do.
     */
    public static void runRequestedLockReload(MinecraftServer server) {
        if (!reloadRequested) return;
        reloadRequested = false;

        if (!net.bananemdnsa.historystages.data.lock.VisibleRecipes.gatedSetChanged(
                server.getRecipeManager().getOrderedRecipes())) {
            resyncRecipes(server);
            return;
        }

        server.reloadResources(server.getPackRepository().getSelectedIds())
                .exceptionally(e -> {
                    DebugLogger.warn("Recipe Locks",
                            "Reloading datapacks after a stage change failed: " + e.getMessage()
                                    + ". Machines that keep their own copy of the recipe list may "
                                    + "still be going by the old one until /reload.");
                    return null;
                });
    }

    /**
     * Resends the recipe list, and the recipe book behind it.
     *
     * <p><strong>Never send the list on its own.</strong> {@code handleUpdateRecipes} throws away
     * every {@code RecipeCollection} the client had and builds new ones, and a new collection
     * knows none of the player's recipes until the book is resent — so the vanilla recipe book at
     * the crafting table goes blank and stays blank, past an F3+T, until the player rejoins.
     *
     * <p>Vanilla pairs the two everywhere it sends them: on join and in
     * {@code PlayerList.reloadResources}, which is also what covers the reload branch above.
     *
     * <p>{@code getOrderedRecipes} rather than {@code getRecipes} because that is what vanilla
     * puts in this packet — and because {@code getRecipes} is gated on the server now, so sending
     * it would take the locked recipes off every client and out of their recipe book.
     */
    private static void resyncRecipes(MinecraftServer server) {
        ClientboundUpdateRecipesPacket recipePacket = new ClientboundUpdateRecipesPacket(
                server.getRecipeManager().getOrderedRecipes());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(recipePacket);
            p.getRecipeBook().sendInitialRecipeBook(p);
        }
    }

    private static volatile boolean reloadRequested;
}
