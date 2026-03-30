package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestEditorDataPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestEditorDataPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_editor_data"));

    public static final StreamCodec<FriendlyByteBuf, RequestEditorDataPacket> STREAM_CODEC =
            StreamCodec.of(RequestEditorDataPacket::encode, RequestEditorDataPacket::decode);

    private static void encode(FriendlyByteBuf buffer, RequestEditorDataPacket msg) {
        // No data needed
    }

    private static RequestEditorDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestEditorDataPacket();
    }

    public static void handle(RequestEditorDataPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                if (!player.hasPermissions(2)) return;
                PacketDistributor.sendToPlayer(player, new EditorSyncPacket(StageManager.getStages()));
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
