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
                        ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "main"),
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

        // Send a packet from client to server
        public static void sendToServer(Object packet) {
                INSTANCE.sendToServer(packet);
        }
}