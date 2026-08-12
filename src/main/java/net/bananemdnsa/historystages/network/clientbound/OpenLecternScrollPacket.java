package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.scroll.ClientLecternScrollHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server tells one reader which stage the scroll on a lectern carries.
 *
 * <p>The lectern block entity never syncs its item — it overrides neither {@code getUpdateTag} nor
 * {@code getUpdatePacket} — so the client cannot work this out for itself, however long it looks
 * at the block.
 */
public record OpenLecternScrollPacket(String stageId, BlockPos lecternPos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenLecternScrollPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "open_lectern_scroll"));

    public static final StreamCodec<FriendlyByteBuf, OpenLecternScrollPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeUtf(msg.stageId);
                        buf.writeBlockPos(msg.lecternPos);
                    },
                    buf -> new OpenLecternScrollPacket(buf.readUtf(), buf.readBlockPos()));

    public static void handle(OpenLecternScrollPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientLecternScrollHandler.open(msg.stageId, msg.lecternPos));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
