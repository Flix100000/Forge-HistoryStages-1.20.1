package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StageDefinitionsPayload(String globalStagesJson, String individualStagesJson) implements CustomPacketPayload {
    public static final Type<StageDefinitionsPayload> TYPE = new Type<>(HistoryStages.id("stage_definitions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StageDefinitionsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, StageDefinitionsPayload::globalStagesJson,
            ByteBufCodecs.STRING_UTF8, StageDefinitionsPayload::individualStagesJson,
            StageDefinitionsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
