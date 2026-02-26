package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HistoryStages.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, SyncStagesPacket.class, SyncStagesPacket::encode, SyncStagesPacket::decode, SyncStagesPacket::handle);
    }

    // Hilfsmethode, um das Paket an alle Spieler zu senden
    public static void sendToAll(SyncStagesPacket packet) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }

    // Hilfsmethode, um das Paket an einen bestimmten Spieler zu senden (z.B. beim Login)
    public static void sendToPlayer(SyncStagesPacket packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}