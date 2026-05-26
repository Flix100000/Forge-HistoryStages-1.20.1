package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record LockFeedbackPayload(byte kind, List<String> displayNames) implements CustomPacketPayload {
    public static final byte KIND_DIMENSION = 0;
    public static final byte KIND_MOB = 1;

    public static final Type<LockFeedbackPayload> TYPE = new Type<>(HistoryStages.id("lock_feedback"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> LIST_CODEC = new StreamCodec<>() {
        @Override
        public List<String> decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<String> list = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) list.add(buf.readUtf());
            return list;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, List<String> value) {
            buf.writeVarInt(value.size());
            for (String s : value) buf.writeUtf(s);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, LockFeedbackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, LockFeedbackPayload::kind,
            LIST_CODEC, LockFeedbackPayload::displayNames,
            LockFeedbackPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
