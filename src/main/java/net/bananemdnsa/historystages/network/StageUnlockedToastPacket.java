package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StageUnlockedToastPacket(String stageName, String iconId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StageUnlockedToastPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "toast"));

    public static final StreamCodec<FriendlyByteBuf, StageUnlockedToastPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> { buf.writeUtf(msg.stageName); buf.writeUtf(msg.iconId); },
                    buf -> new StageUnlockedToastPacket(buf.readUtf(), buf.readUtf())
            );

    public static void handle(StageUnlockedToastPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.bananemdnsa.historystages.client.ClientToastHandler.showToast(msg.stageName, msg.iconId);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
