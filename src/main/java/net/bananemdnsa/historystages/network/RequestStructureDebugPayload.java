package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestStructureDebugPayload() implements CustomPacketPayload {
    public static final Type<RequestStructureDebugPayload> TYPE = new Type<>(HistoryStages.id("request_structure_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestStructureDebugPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestStructureDebugPayload decode(RegistryFriendlyByteBuf buffer) {
            return new RequestStructureDebugPayload();
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RequestStructureDebugPayload payload) {
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
