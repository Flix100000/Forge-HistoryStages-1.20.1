package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestEditorDataPacket {

    public RequestEditorDataPacket() {}

    public static void encode(RequestEditorDataPacket msg, FriendlyByteBuf buffer) {
        // No data needed
    }

    public static RequestEditorDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestEditorDataPacket();
    }

    public static void handle(RequestEditorDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            // Send all stage data back to the client
            PacketHandler.INSTANCE.reply(
                    new EditorSyncPacket(StageManager.getStages()),
                    ctx.get()
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
