package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ToggleStageLockPacket(String stageId, boolean unlocked) implements CustomPacketPayload {
    public static final Type<ToggleStageLockPacket> TYPE = new Type<>(HistoryStages.id("toggle_stage_lock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleStageLockPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ToggleStageLockPacket::stageId,
            ByteBufCodecs.BOOL, ToggleStageLockPacket::unlocked,
            ToggleStageLockPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
