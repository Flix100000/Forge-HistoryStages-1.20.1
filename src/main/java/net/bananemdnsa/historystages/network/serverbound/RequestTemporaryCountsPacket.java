package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.saveddata.TemporaryStageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server: requests the live unlock counts for temporary stages. The server
 * replies with {@link SyncTemporaryCountsPacket}. Sent by the stage overview editor on
 * open and periodically while open.
 *
 * <p>{@code individualTarget} is the player the editor's picker is on, or null for "@a".
 * Individual temporary state is per player and only the picked one is worth sending — the
 * rows show a single count, and "@a" has no single count to show.
 */
public class RequestTemporaryCountsPacket {

    private final UUID individualTarget;

    public RequestTemporaryCountsPacket(UUID individualTarget) {
        this.individualTarget = individualTarget;
    }

    public static void encode(RequestTemporaryCountsPacket msg, FriendlyByteBuf buffer) {
        boolean hasTarget = msg.individualTarget != null;
        buffer.writeBoolean(hasTarget);
        if (hasTarget) buffer.writeUUID(msg.individualTarget);
    }

    public static RequestTemporaryCountsPacket decode(FriendlyByteBuf buffer) {
        return new RequestTemporaryCountsPacket(buffer.readBoolean() ? buffer.readUUID() : null);
    }

    public static void handle(RequestTemporaryCountsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            TemporaryStageData temp = TemporaryStageData.get(player.serverLevel());

            Map<String, Integer> counts = new HashMap<>();
            Map<String, Long> activeTicks = new HashMap<>();
            for (Map.Entry<String, StageEntry> e : StageManager.getStages().entrySet()) {
                if (e.getValue().getMode() != StageMode.TEMPORARY) continue;
                String id = e.getKey();
                counts.put(id, temp.getGlobalCount(id));
                long remaining = temp.globalActiveTicks(id);
                if (remaining > 0) activeTicks.put(id, remaining);
            }

            UUID target = msg.individualTarget;
            Map<String, Integer> individualCounts = new HashMap<>();
            Map<String, Long> individualActiveTicks = new HashMap<>();
            if (target != null) {
                for (Map.Entry<String, StageEntry> e : StageManager.getIndividualStages().entrySet()) {
                    if (e.getValue().getMode() != StageMode.TEMPORARY) continue;
                    String id = e.getKey();
                    individualCounts.put(id, temp.getIndividualCount(target, id));
                    long remaining = temp.individualActiveTicks(target, id);
                    if (remaining > 0) individualActiveTicks.put(id, remaining);
                }
            }

            PacketHandler.INSTANCE.reply(new SyncTemporaryCountsPacket(
                    counts, activeTicks, target, individualCounts, individualActiveTicks), ctx.get());
        });
        ctx.get().setPacketHandled(true);
    }
}
