package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record UnlockedStagesPayload(List<String> stages) implements CustomPacketPayload {
    public static final Type<UnlockedStagesPayload> TYPE = new Type<>(HistoryStages.id("unlocked_stages"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockedStagesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), UnlockedStagesPayload::stages,
            UnlockedStagesPayload::new
    );

    public UnlockedStagesPayload {
        stages = new ArrayList<>(stages);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
