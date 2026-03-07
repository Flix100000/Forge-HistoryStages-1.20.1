package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
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
        registrar.playToClient(SyncStagesPacket.TYPE, SyncStagesPacket.STREAM_CODEC, SyncStagesPacket::handle);
        registrar.playToClient(StageUnlockedToastPacket.TYPE, StageUnlockedToastPacket.STREAM_CODEC, StageUnlockedToastPacket::handle);
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
}
