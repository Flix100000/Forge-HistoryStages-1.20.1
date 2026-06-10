package net.bananemdnsa.historystages.network.clientbound;
import net.bananemdnsa.historystages.network.EditorDataCache;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → Client: live state of global temporary stages — unlock counts
 * (stageId → times unlocked) and, for currently-unlocked stages, the remaining
 * game ticks until auto re-lock. Stored in {@link EditorDataCache} for the editor.
 */
public record SyncTemporaryCountsPacket(Map<String, Integer> counts,
                                        Map<String, Long> activeTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncTemporaryCountsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_temporary_counts"));

    public static final StreamCodec<FriendlyByteBuf, SyncTemporaryCountsPacket> STREAM_CODEC =
            StreamCodec.of(SyncTemporaryCountsPacket::encode, SyncTemporaryCountsPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncTemporaryCountsPacket msg) {
        buffer.writeVarInt(msg.counts.size());
        for (Map.Entry<String, Integer> e : msg.counts.entrySet()) {
            buffer.writeUtf(e.getKey());
            buffer.writeVarInt(e.getValue());
        }
        buffer.writeVarInt(msg.activeTicks.size());
        for (Map.Entry<String, Long> e : msg.activeTicks.entrySet()) {
            buffer.writeUtf(e.getKey());
            buffer.writeVarLong(e.getValue());
        }
    }

    private static SyncTemporaryCountsPacket decode(FriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String id = buffer.readUtf();
            counts.put(id, buffer.readVarInt());
        }
        int m = buffer.readVarInt();
        Map<String, Long> active = new HashMap<>();
        for (int i = 0; i < m; i++) {
            String id = buffer.readUtf();
            active.put(id, buffer.readVarLong());
        }
        return new SyncTemporaryCountsPacket(counts, active);
    }

    public static void handle(SyncTemporaryCountsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            EditorDataCache.setTemporaryCounts(msg.counts);
            EditorDataCache.setTemporaryActiveTicks(msg.activeTicks);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
