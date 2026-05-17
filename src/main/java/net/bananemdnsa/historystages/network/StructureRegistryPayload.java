package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record StructureRegistryPayload(List<String> structureIds, List<String> structureTagIds)
        implements CustomPacketPayload {
    public static final Type<StructureRegistryPayload> TYPE = new Type<>(HistoryStages.id("structure_registry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StructureRegistryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StructureRegistryPayload::structureIds,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StructureRegistryPayload::structureTagIds,
            StructureRegistryPayload::new
    );

    public StructureRegistryPayload {
        structureIds = new ArrayList<>(structureIds);
        structureTagIds = new ArrayList<>(structureTagIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
