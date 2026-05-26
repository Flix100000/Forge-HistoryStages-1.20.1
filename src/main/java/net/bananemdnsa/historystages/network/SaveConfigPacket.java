package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Map;

public record SaveConfigPacket(Map<String, String> clientValues, Map<String, String> commonValues) implements CustomPacketPayload {
    public static final Type<SaveConfigPacket> TYPE = new Type<>(HistoryStages.id("save_config"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, String>> MAP_CODEC =
            ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveConfigPacket> STREAM_CODEC = StreamCodec.composite(
            MAP_CODEC, SaveConfigPacket::clientValues,
            MAP_CODEC, SaveConfigPacket::commonValues,
            SaveConfigPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
