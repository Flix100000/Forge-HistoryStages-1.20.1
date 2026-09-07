package net.bananemdnsa.historystages.network.clientbound;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.TradeLockNotice;
import net.bananemdnsa.historystages.data.lock.TradeLockKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells the client that the trade window just opened is empty because of stage locks.
 *
 * <p>A packet of its own rather than another kind of {@code LockFeedbackPacket}, because it goes
 * somewhere else. That one writes a line to the chat or the actionbar and is done; this one has
 * to be remembered for as long as a window stays open, so it can be drawn where the offers would
 * have been.
 *
 * <p>Sent immediately after the window is opened, and carries the container id that opening
 * returned — see {@link TradeLockNotice} for why the id and not just the names.
 *
 * @param stageNames display names of the stages holding the offers back, in registration order.
 *                   Names rather than ids: the client has no reliable way to turn one into the
 *                   other for an individual stage, and this text is for a player to read.
 * @param kind       whether global, individual or both kinds of stage held the offers back, which
 *                   decides the lock the notice draws. Sent rather than worked out on the client
 *                   for the same reason the names are: from a display name there is no way back
 *                   to an individual stage.
 */
public record TradeLockedPacket(int containerId, List<String> stageNames, TradeLockKind kind)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradeLockedPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "trade_locked"));

    public static final StreamCodec<FriendlyByteBuf, TradeLockedPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeVarInt(msg.containerId);
                        buf.writeVarInt(msg.stageNames.size());
                        for (String name : msg.stageNames) {
                            buf.writeUtf(name);
                        }
                        buf.writeVarInt(msg.kind.code());
                    },
                    buf -> {
                        int containerId = buf.readVarInt();
                        int size = buf.readVarInt();
                        List<String> names = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            names.add(buf.readUtf());
                        }
                        TradeLockKind kind = TradeLockKind.fromCode(buf.readVarInt());
                        return new TradeLockedPacket(containerId, names, kind);
                    }
            );

    public static void handle(TradeLockedPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> TradeLockNotice.set(msg.containerId(), msg.stageNames(), msg.kind()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
