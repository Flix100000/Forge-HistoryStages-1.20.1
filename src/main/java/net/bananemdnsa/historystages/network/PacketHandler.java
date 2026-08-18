package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.resources.ResourceLocation;
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
}