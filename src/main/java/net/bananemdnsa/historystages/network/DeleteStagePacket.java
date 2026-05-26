package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DeleteStagePacket(String stageId, boolean individual) implements CustomPacketPayload {
    public static final Type<DeleteStagePacket> TYPE = new Type<>(HistoryStages.id("delete_stage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteStagePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeleteStagePacket::stageId,
            ByteBufCodecs.BOOL, DeleteStagePacket::individual,
            DeleteStagePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
