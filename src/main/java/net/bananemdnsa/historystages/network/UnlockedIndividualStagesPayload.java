package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record UnlockedIndividualStagesPayload(List<String> stages) implements CustomPacketPayload {
    public static final Type<UnlockedIndividualStagesPayload> TYPE = new Type<>(HistoryStages.id("unlocked_individual_stages"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockedIndividualStagesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), UnlockedIndividualStagesPayload::stages,
            UnlockedIndividualStagesPayload::new
    );

    public UnlockedIndividualStagesPayload {
        stages = new ArrayList<>(stages);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
