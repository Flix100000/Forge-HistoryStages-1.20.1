package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client asks a pedestal to start or pause. Everything is re-checked server-side; the
 * button the player sees is a display, not an authority.
 */
public record PedestalControlPacket(BlockPos pos, boolean start) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PedestalControlPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "pedestal_control"));

    public static final StreamCodec<FriendlyByteBuf, PedestalControlPacket> STREAM_CODEC =
            StreamCodec.of(PedestalControlPacket::encode, PedestalControlPacket::decode);

    private static void encode(FriendlyByteBuf buf, PedestalControlPacket packet) {
        buf.writeBlockPos(packet.pos);
        buf.writeBoolean(packet.start);
    }

    private static PedestalControlPacket decode(FriendlyByteBuf buf) {
        return new PedestalControlPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(PedestalControlPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Reach check: refuse anything the player could not plausibly be using.
            if (!player.level().isLoaded(packet.pos())) return;
            if (player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5) > 64.0) return;

            BlockEntity be = player.level().getBlockEntity(packet.pos());
            if (!(be instanceof ResearchPedestalBlockEntity pedestal)) return;

            if (packet.start()) {
                pedestal.tryStart(player);
            } else {
                pedestal.pause();
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
