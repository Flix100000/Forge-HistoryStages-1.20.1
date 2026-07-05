package net.bananemdnsa.historystages.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server → Client: live state of global temporary stages — unlock counts
 * (stageId → times unlocked) and, for currently-unlocked stages, the remaining
 * game ticks until auto re-lock. Stored in {@link EditorDataCache} for the editor.
 */
public class SyncTemporaryCountsPacket {

    private final Map<String, Integer> counts;
    private final Map<String, Long> activeTicks;

    public SyncTemporaryCountsPacket(Map<String, Integer> counts, Map<String, Long> activeTicks) {
        this.counts = counts != null ? counts : new HashMap<>();
        this.activeTicks = activeTicks != null ? activeTicks : new HashMap<>();
    }

    public static void encode(SyncTemporaryCountsPacket msg, FriendlyByteBuf buffer) {
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

    public static SyncTemporaryCountsPacket decode(FriendlyByteBuf buffer) {
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

    public static void handle(SyncTemporaryCountsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            EditorDataCache.setTemporaryCounts(msg.counts);
            EditorDataCache.setTemporaryActiveTicks(msg.activeTicks);
        });
        ctx.get().setPacketHandled(true);
    }
}
