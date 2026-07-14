package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.saveddata.TemporaryStageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client → Server: requests the live unlock counts for global temporary stages.
 * Lightweight (no payload); the server replies with {@link SyncTemporaryCountsPacket}.
 * Sent by the stage overview editor on open and periodically while open.
 */
public class RequestTemporaryCountsPacket {

    public RequestTemporaryCountsPacket() {}

    public static void encode(RequestTemporaryCountsPacket msg, FriendlyByteBuf buffer) {
        // No data needed
    }

    public static RequestTemporaryCountsPacket decode(FriendlyByteBuf buffer) {
        return new RequestTemporaryCountsPacket();
    }

    public static void handle(RequestTemporaryCountsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            Map<String, Integer> counts = new HashMap<>();
            Map<String, Long> activeTicks = new HashMap<>();
            TemporaryStageData temp = TemporaryStageData.get(player.serverLevel());
            for (Map.Entry<String, net.bananemdnsa.historystages.data.StageEntry> e : StageManager.getStages().entrySet()) {
                if (e.getValue().getMode() == StageMode.TEMPORARY) {
                    String id = e.getKey();
                    counts.put(id, temp.getGlobalCount(id));
                    long remaining = temp.globalActiveTicks(id);
                    if (remaining > 0) activeTicks.put(id, remaining);
                }
            }
            PacketHandler.INSTANCE.reply(new SyncTemporaryCountsPacket(counts, activeTicks), ctx.get());
        });
        ctx.get().setPacketHandled(true);
    }
}
