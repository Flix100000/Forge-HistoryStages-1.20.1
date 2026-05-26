package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StageUnlockedToastPayload(String stageName, String iconId) implements CustomPacketPayload {
    public static final Type<StageUnlockedToastPayload> TYPE = new Type<>(HistoryStages.id("stage_unlocked_toast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StageUnlockedToastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, StageUnlockedToastPayload::stageName,
            ByteBufCodecs.STRING_UTF8, StageUnlockedToastPayload::iconId,
            StageUnlockedToastPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
