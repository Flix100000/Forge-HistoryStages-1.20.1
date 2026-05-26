package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DepositDependencyPacket(BlockPos blockPos, int groupIndex, String depType, String data) implements CustomPacketPayload {
    public static final Type<DepositDependencyPacket> TYPE = new Type<>(HistoryStages.id("deposit_dependency"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DepositDependencyPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DepositDependencyPacket::blockPos,
            ByteBufCodecs.VAR_INT, DepositDependencyPacket::groupIndex,
            ByteBufCodecs.STRING_UTF8, DepositDependencyPacket::depType,
            ByteBufCodecs.STRING_UTF8, DepositDependencyPacket::data,
            DepositDependencyPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
