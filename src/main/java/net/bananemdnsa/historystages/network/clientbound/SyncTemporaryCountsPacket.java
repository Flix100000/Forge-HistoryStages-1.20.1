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
import java.util.UUID;

/**
 * Server → Client: live state of temporary stages — unlock counts (stageId → times
 * unlocked) and, for currently-unlocked stages, the remaining game ticks until auto
 * re-lock. Stored in {@link EditorDataCache} for the editor.
 *
 * <p>The global maps always travel. The individual ones describe {@code individualTarget}
 * and are empty when the editor asked for none (its player picker on "@a", where a single
 * count would have no meaning). The target is carried rather than assumed, so a reply that
 * arrives after the picker moved on cannot be read as the newly picked player's state.
 */
public record SyncTemporaryCountsPacket(Map<String, Integer> counts,
                                        Map<String, Long> activeTicks,
                                        UUID individualTarget,
                                        Map<String, Integer> individualCounts,
                                        Map<String, Long> individualActiveTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncTemporaryCountsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_temporary_counts"));

    public static final StreamCodec<FriendlyByteBuf, SyncTemporaryCountsPacket> STREAM_CODEC =
            StreamCodec.of(SyncTemporaryCountsPacket::encode, SyncTemporaryCountsPacket::decode);

    private static void writeCounts(FriendlyByteBuf buffer, Map<String, Integer> counts) {
        buffer.writeVarInt(counts.size());
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            buffer.writeUtf(e.getKey());
            buffer.writeVarInt(e.getValue());
        }
    }

    private static void writeTicks(FriendlyByteBuf buffer, Map<String, Long> ticks) {
        buffer.writeVarInt(ticks.size());
        for (Map.Entry<String, Long> e : ticks.entrySet()) {
            buffer.writeUtf(e.getKey());
            buffer.writeVarLong(e.getValue());
        }
    }

    private static Map<String, Integer> readCounts(FriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String id = buffer.readUtf();
            counts.put(id, buffer.readVarInt());
        }
        return counts;
    }

    private static Map<String, Long> readTicks(FriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        Map<String, Long> ticks = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String id = buffer.readUtf();
            ticks.put(id, buffer.readVarLong());
        }
        return ticks;
    }

    private static void encode(FriendlyByteBuf buffer, SyncTemporaryCountsPacket msg) {
        writeCounts(buffer, msg.counts);
        writeTicks(buffer, msg.activeTicks);

        boolean hasTarget = msg.individualTarget != null;
        buffer.writeBoolean(hasTarget);
        if (!hasTarget) return; // the individual maps are empty without a target
        buffer.writeUUID(msg.individualTarget);
        writeCounts(buffer, msg.individualCounts);
        writeTicks(buffer, msg.individualActiveTicks);
    }

    private static SyncTemporaryCountsPacket decode(FriendlyByteBuf buffer) {
        Map<String, Integer> counts = readCounts(buffer);
        Map<String, Long> active = readTicks(buffer);

        if (!buffer.readBoolean()) {
            return new SyncTemporaryCountsPacket(counts, active, null, new HashMap<>(), new HashMap<>());
        }
        UUID target = buffer.readUUID();
        return new SyncTemporaryCountsPacket(counts, active, target, readCounts(buffer), readTicks(buffer));
    }

    public static void handle(SyncTemporaryCountsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            EditorDataCache.setTemporaryCounts(msg.counts);
            EditorDataCache.setTemporaryActiveTicks(msg.activeTicks);
            EditorDataCache.setIndividualTemporary(msg.individualTarget, msg.individualCounts,
                    msg.individualActiveTicks);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
