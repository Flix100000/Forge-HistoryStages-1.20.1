package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.TradeGoodsScanner;
import net.bananemdnsa.historystages.network.clientbound.SyncTradeGoodsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The editor asking which items merchants here deal in.
 *
 * <p>Asked rather than pushed, because almost nobody opens the trades tab and rolling the answer
 * costs a moment. The first ask pays for it; every later one reads the server's cached list.
 *
 * <p>Operator-gated like the rest of the editor's traffic. Nothing here is secret, but a packet
 * that makes the server do work on request is one any client could send.
 */
public record RequestTradeGoodsPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestTradeGoodsPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_trade_goods"));

    public static final StreamCodec<FriendlyByteBuf, RequestTradeGoodsPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> { }, buf -> new RequestTradeGoodsPacket());

    public static void handle(RequestTradeGoodsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            PacketDistributor.sendToPlayer(player,
                    new SyncTradeGoodsPacket(TradeGoodsScanner.cached(player.level())));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
