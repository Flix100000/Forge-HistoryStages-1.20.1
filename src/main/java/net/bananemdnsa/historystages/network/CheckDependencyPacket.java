package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CheckDependencyPacket(String stageId, boolean individual, BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<CheckDependencyPacket> TYPE = new Type<>(HistoryStages.id("check_dependency"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CheckDependencyPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CheckDependencyPacket::stageId,
            ByteBufCodecs.BOOL, CheckDependencyPacket::individual,
            BlockPos.STREAM_CODEC, CheckDependencyPacket::blockPos,
            CheckDependencyPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
