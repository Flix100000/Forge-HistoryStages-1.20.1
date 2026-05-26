package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

public record SyncConfigPayload(Map<String, String> commonValues) implements CustomPacketPayload {
    public static final Type<SyncConfigPayload> TYPE = new Type<>(HistoryStages.id("sync_config"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, String>> MAP_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncConfigPayload> STREAM_CODEC = StreamCodec.composite(
            MAP_CODEC, SyncConfigPayload::commonValues,
            SyncConfigPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
