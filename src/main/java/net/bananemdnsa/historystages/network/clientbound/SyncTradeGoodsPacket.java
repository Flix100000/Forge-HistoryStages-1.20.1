package net.bananemdnsa.historystages.network.clientbound;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.ClientTradeGoods;
import net.bananemdnsa.historystages.data.lock.TradePreview;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The items merchants on this server deal in, for the editor's trades picker.
 *
 * <p>A few hundred offers, sent once when the editor asks. Whole offers rather than a list of
 * ids, because the picker shows trades: "the librarian's book trade" is what a pack author is
 * looking for, and an item id on its own cannot say which trade it belongs to.
 *
 * <p>Only a dedicated server has anything to add — a client's copy of the recipe table holds the
 * vanilla recipes and nothing a mod contributed. It is sent anyway rather than worked out, because
 * the client cannot tell which case it is in.
 */
public record SyncTradeGoodsPacket(List<TradePreview> offers) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncTradeGoodsPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_trade_goods"));

    public static final StreamCodec<FriendlyByteBuf, SyncTradeGoodsPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeVarInt(msg.offers.size());
                        for (TradePreview offer : msg.offers) {
                            buf.writeUtf(offer.professionId());
                            buf.writeVarInt(offer.level());
                            buf.writeUtf(offer.resultId());
                            buf.writeVarInt(offer.resultCount());
                            writeOptional(buf, offer.costAId(), offer.costACount());
                            writeOptional(buf, offer.costBId(), offer.costBCount());
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<TradePreview> offers = new ArrayList<>(Math.max(16, size));
                        for (int i = 0; i < size; i++) {
                            String professionId = buf.readUtf();
                            int level = buf.readVarInt();
                            String resultId = buf.readUtf();
                            int resultCount = buf.readVarInt();
                            String costAId = buf.readBoolean() ? buf.readUtf() : null;
                            int costACount = costAId == null ? 0 : buf.readVarInt();
                            String costBId = buf.readBoolean() ? buf.readUtf() : null;
                            int costBCount = costBId == null ? 0 : buf.readVarInt();
                            offers.add(new TradePreview(professionId, level, resultId, resultCount,
                                    costAId, costACount, costBId, costBCount));
                        }
                        return new SyncTradeGoodsPacket(offers);
                    }
            );

    /** Most offers have one price, so the second is written as a flag rather than an empty id. */
    private static void writeOptional(FriendlyByteBuf buf, String id, int count) {
        buf.writeBoolean(id != null);
        if (id == null) return;
        buf.writeUtf(id);
        buf.writeVarInt(count);
    }

    public static void handle(SyncTradeGoodsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTradeGoods.addFromServer(msg.offers()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
