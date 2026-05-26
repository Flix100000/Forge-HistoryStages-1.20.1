package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveStagePacket(String stageId, String entryJson, boolean individual) implements CustomPacketPayload {
    public static final Type<SaveStagePacket> TYPE = new Type<>(HistoryStages.id("save_stage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveStagePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SaveStagePacket::stageId,
            ByteBufCodecs.STRING_UTF8, SaveStagePacket::entryJson,
            ByteBufCodecs.BOOL, SaveStagePacket::individual,
            SaveStagePacket::new
    );

    public static SaveStagePacket of(String stageId, StageEntry entry, boolean individual) {
        return new SaveStagePacket(stageId, entry.toJson(), individual);
    }

    public static SaveStagePacket of(String stageId, StageEntry entry) {
        return of(stageId, entry, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
