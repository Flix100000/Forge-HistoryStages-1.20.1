package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.UngatedRecipes;
import net.bananemdnsa.historystages.data.lock.VisibleRecipes;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
        private static final String PROTOCOL_VERSION = "9";
        public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(HistoryStages.MOD_ID, "main"),
                        () -> PROTOCOL_VERSION,
                        PROTOCOL_VERSION::equals,
                        PROTOCOL_VERSION::equals);

        public static void register() {
                int id = 0;
                INSTANCE.registerMessage(id++, SyncStagesPacket.class, SyncStagesPacket::encode,
                                SyncStagesPacket::decode,
                                SyncStagesPacket::handle);
                INSTANCE.registerMessage(id++, StageUnlockedToastPacket.class, StageUnlockedToastPacket::encode,
                                StageUnlockedToastPacket::decode, StageUnlockedToastPacket::handle);
                INSTANCE.registerMessage(id++, RequestEditorDataPacket.class, RequestEditorDataPacket::encode,
                                RequestEditorDataPacket::decode, RequestEditorDataPacket::handle);
                INSTANCE.registerMessage(id++, EditorSyncPacket.class, EditorSyncPacket::encode,
                                EditorSyncPacket::decode,
                                EditorSyncPacket::handle);
                INSTANCE.registerMessage(id++, SaveStagePacket.class, SaveStagePacket::encode, SaveStagePacket::decode,
                                SaveStagePacket::handle);
                INSTANCE.registerMessage(id++, DeleteStagePacket.class, DeleteStagePacket::encode,
                                DeleteStagePacket::decode,
                                DeleteStagePacket::handle);
                INSTANCE.registerMessage(id++, ToggleStageLockPacket.class, ToggleStageLockPacket::encode,
                                ToggleStageLockPacket::decode, ToggleStageLockPacket::handle);
                INSTANCE.registerMessage(id++, SaveConfigPacket.class, SaveConfigPacket::encode,
                                SaveConfigPacket::decode,
                                SaveConfigPacket::handle);
                INSTANCE.registerMessage(id++, SyncStageDefinitionsPacket.class, SyncStageDefinitionsPacket::encode,
                                SyncStageDefinitionsPacket::decode, SyncStageDefinitionsPacket::handle);
                INSTANCE.registerMessage(id++, SyncConfigPacket.class, SyncConfigPacket::encode,
                                SyncConfigPacket::decode,
                                SyncConfigPacket::handle);
                INSTANCE.registerMessage(id++, SyncIndividualStagesPacket.class, SyncIndividualStagesPacket::encode,
                                SyncIndividualStagesPacket::decode, SyncIndividualStagesPacket::handle);
                INSTANCE.registerMessage(id++, CheckDependencyPacket.class, CheckDependencyPacket::encode,
                                CheckDependencyPacket::decode, CheckDependencyPacket::handle);
                INSTANCE.registerMessage(id++, SyncDependencyStatusPacket.class, SyncDependencyStatusPacket::encode,
                                SyncDependencyStatusPacket::decode, SyncDependencyStatusPacket::handle);
                INSTANCE.registerMessage(id++, DepositDependencyPacket.class, DepositDependencyPacket::toBytes,
                                DepositDependencyPacket::new, DepositDependencyPacket::handle);
                INSTANCE.registerMessage(id++, SyncStructureRegistryPacket.class, SyncStructureRegistryPacket::encode,
                                SyncStructureRegistryPacket::decode, SyncStructureRegistryPacket::handle);
                INSTANCE.registerMessage(id++, LockFeedbackPacket.class, LockFeedbackPacket::encode,
                                LockFeedbackPacket::decode, LockFeedbackPacket::handle);
                INSTANCE.registerMessage(id++, RequestStructureDebugPacket.class,
                                RequestStructureDebugPacket::encode,
                                RequestStructureDebugPacket::decode, RequestStructureDebugPacket::handle);
                INSTANCE.registerMessage(id++, EditorFeedbackPacket.class,
                                EditorFeedbackPacket::encode,
                                EditorFeedbackPacket::decode, EditorFeedbackPacket::handle);
                INSTANCE.registerMessage(id++, SyncLockBordersPacket.class,
                                SyncLockBordersPacket::encode,
                                SyncLockBordersPacket::decode, SyncLockBordersPacket::handle);
                INSTANCE.registerMessage(id++, RequestClusterShapesPacket.class,
                                RequestClusterShapesPacket::encode,
                                RequestClusterShapesPacket::decode, RequestClusterShapesPacket::handle);
                INSTANCE.registerMessage(id++, ToggleStructureVizPacket.class,
                                ToggleStructureVizPacket::encode,
                                ToggleStructureVizPacket::decode, ToggleStructureVizPacket::handle);
                INSTANCE.registerMessage(id++, RequestTemporaryCountsPacket.class,
                                RequestTemporaryCountsPacket::encode,
                                RequestTemporaryCountsPacket::decode, RequestTemporaryCountsPacket::handle);
                INSTANCE.registerMessage(id++, SyncTemporaryCountsPacket.class,
                                SyncTemporaryCountsPacket::encode,
                                SyncTemporaryCountsPacket::decode, SyncTemporaryCountsPacket::handle);
                INSTANCE.registerMessage(id++, RequestIndividualStatesPacket.class,
                                RequestIndividualStatesPacket::encode,
                                RequestIndividualStatesPacket::decode, RequestIndividualStatesPacket::handle);
                INSTANCE.registerMessage(id++, SyncIndividualStatesPacket.class,
                                SyncIndividualStatesPacket::encode,
                                SyncIndividualStatesPacket::decode, SyncIndividualStatesPacket::handle);
                INSTANCE.registerMessage(id++, ToggleIndividualStageLockPacket.class,
                                ToggleIndividualStageLockPacket::encode,
                                ToggleIndividualStageLockPacket::decode,
                                ToggleIndividualStageLockPacket::handle);
                INSTANCE.registerMessage(id++, CreateFolderPacket.class,
                                CreateFolderPacket::encode,
                                CreateFolderPacket::decode, CreateFolderPacket::handle);
                INSTANCE.registerMessage(id++, RenameFolderPacket.class,
                                RenameFolderPacket::encode,
                                RenameFolderPacket::decode, RenameFolderPacket::handle);
                INSTANCE.registerMessage(id++, DeleteFolderPacket.class,
                                DeleteFolderPacket::encode,
                                DeleteFolderPacket::decode, DeleteFolderPacket::handle);
                INSTANCE.registerMessage(id++, MoveStagesPacket.class,
                                MoveStagesPacket::encode,
                                MoveStagesPacket::decode, MoveStagesPacket::handle);
                INSTANCE.registerMessage(id++, MoveFoldersPacket.class,
                                MoveFoldersPacket::encode,
                                MoveFoldersPacket::decode, MoveFoldersPacket::handle);
                INSTANCE.registerMessage(id++, SyncGraphConfigPacket.class,
                                SyncGraphConfigPacket::encode,
                                SyncGraphConfigPacket::decode, SyncGraphConfigPacket::handle);
                INSTANCE.registerMessage(id++, RequestStageDependencyPacket.class,
                                RequestStageDependencyPacket::encode,
                                RequestStageDependencyPacket::decode, RequestStageDependencyPacket::handle);
                INSTANCE.registerMessage(id++, SaveGraphPositionsPacket.class,
                                SaveGraphPositionsPacket::encode,
                                SaveGraphPositionsPacket::decode, SaveGraphPositionsPacket::handle);
                INSTANCE.registerMessage(id++, RearrangeGraphPacket.class,
                                RearrangeGraphPacket::encode,
                                RearrangeGraphPacket::decode, RearrangeGraphPacket::handle);
                INSTANCE.registerMessage(id++, SaveStageGraphInfoPacket.class,
                                SaveStageGraphInfoPacket::encode,
                                SaveStageGraphInfoPacket::decode, SaveStageGraphInfoPacket::handle);
                INSTANCE.registerMessage(id++, SaveGraphConfigPacket.class,
                                SaveGraphConfigPacket::encode,
                                SaveGraphConfigPacket::decode, SaveGraphConfigPacket::handle);
                INSTANCE.registerMessage(id++, SaveStageGraphStylePacket.class,
                                SaveStageGraphStylePacket::encode,
                                SaveStageGraphStylePacket::decode, SaveStageGraphStylePacket::handle);
                INSTANCE.registerMessage(id++, PedestalControlPacket.class,
                                PedestalControlPacket::encode,
                                PedestalControlPacket::decode, PedestalControlPacket::handle);
                INSTANCE.registerMessage(id++, OpenLecternScrollPacket.class,
                                OpenLecternScrollPacket::encode,
                                OpenLecternScrollPacket::decode, OpenLecternScrollPacket::handle);
                INSTANCE.registerMessage(id++, TakeLecternScrollPacket.class,
                                TakeLecternScrollPacket::encode,
                                TakeLecternScrollPacket::decode, TakeLecternScrollPacket::handle);
        }

        // Send the locked-structure border BBs to a specific player.
        public static void sendLockBordersToPlayer(SyncLockBordersPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Send editor-styled toast feedback to a specific player
        public static void sendEditorFeedback(EditorFeedbackPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Send lock feedback (dimension or mob) to a specific player — client decides display
        public static void sendLockFeedbackToPlayer(LockFeedbackPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Send structure registry to a specific player (on login)
        public static void sendStructureRegistryToPlayer(SyncStructureRegistryPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Hilfsmethode, um das Paket an alle Spieler zu senden
        public static void sendToAll(SyncStagesPacket packet) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
        }

        // Hilfsmethode, um das Paket an einen bestimmten Spieler zu senden (z.B. beim
        // Login)
        public static void sendToPlayer(SyncStagesPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Toast-Benachrichtigung an alle Spieler senden
        public static void sendToastToAll(StageUnlockedToastPacket packet) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
        }

        // Send an unlock toast to a specific player (e.g. individual-stage unlock)
        public static void sendToastToPlayer(StageUnlockedToastPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Send stage definitions to a specific player (e.g. on login)
        public static void sendDefinitionsToPlayer(SyncStageDefinitionsPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Send stage definitions to all players (e.g. after editor save/delete)
        public static void sendDefinitionsToAll(SyncStageDefinitionsPacket packet) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
        }

        // Send config to a specific player (e.g. on login)
        public static void sendConfigToPlayer(SyncConfigPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Send config to all players (e.g. after admin saves config)
        public static void sendConfigToAll(SyncConfigPacket packet) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
        }

        // Send individual stages to a specific player
        public static void sendIndividualStagesToPlayer(SyncIndividualStagesPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Send graph.toml to a specific player (e.g. on login)
        public static void sendGraphConfigToPlayer(SyncGraphConfigPacket packet, ServerPlayer player) {
                INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Send graph.toml to all players (e.g. after admin saves the graph config)
        public static void sendGraphConfigToAll(SyncGraphConfigPacket packet) {
                INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
        }

        // Send a packet from client to server
        public static void sendToServer(Object packet) {
                INSTANCE.sendToServer(packet);
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
         * Carries out a requested reload, at most once per tick, and picks the cheapest one that
         * will do.
         *
         * <p><strong>Resending is the normal case.</strong> The gate itself needs nothing: it is
         * consulted when a recipe is asked for, so it flips the moment the stage does. What the
         * resend is for is the clients — it is what makes JEI notice, and without it items hidden
         * by a stage never come back after an unlock.
         *
         * <p><strong>A full datapack reload only when the set of hidden recipes actually
         * changed.</strong> A machine that takes the whole recipe list and searches it itself —
         * Create's basin, and most modded machines — keeps that list until a datapack reload tells
         * it to let go, so without one it would take a lock correctly and then stay stuck after
         * the unlock. But it only needs to be told when the list would come out different, and
         * {@code VisibleRecipes.gatedSetChanged} answers exactly that by working the set out and
         * comparing it. A stage gating blocks, biomes or mobs costs nothing; so does saving a
         * stage in the editor without changing what it gates.
         *
         * <p>The reason for being this careful: reloading every datapack on a large modpack
         * freezes the server for as long as it takes, and this can be reached from an
         * auto-trigger, which fires while people are playing.
         */
        public static void runRequestedLockReload(MinecraftServer server) {
                if (!reloadRequested) return;
                reloadRequested = false;

                if (!VisibleRecipes.gatedSetChanged(
                                UngatedRecipes.of(server.getRecipeManager()))) {
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
         * Pushes the recipes back to every client, and the recipe book along with them.
         *
         * <p>The book has to go with the packet. Replacing the client's recipe manager leaves the
         * book pointing at recipes that are no longer the same objects, and the crafting table
         * goes blank and stays blank, past an F3+T, until the player rejoins. Vanilla pairs the
         * two everywhere it sends them: on join and in {@code PlayerList.reloadResources}, which
         * is also what covers the reload branch above.
         *
         * <p>The ungated list, because {@code getRecipes} is gated on the server now and sending
         * it would take the locked recipes off every client and out of their recipe book.
         */
        private static void resyncRecipes(MinecraftServer server) {
                ClientboundUpdateRecipesPacket recipePacket = new ClientboundUpdateRecipesPacket(
                                UngatedRecipes.of(server.getRecipeManager()));
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.connection.send(recipePacket);
                        p.getRecipeBook().sendInitialRecipeBook(p);
                }
        }

        private static volatile boolean reloadRequested;
}