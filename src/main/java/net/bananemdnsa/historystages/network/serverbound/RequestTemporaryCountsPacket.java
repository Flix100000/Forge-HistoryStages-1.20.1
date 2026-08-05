package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.clientbound.SyncTemporaryCountsPacket;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.saveddata.TemporaryStageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client → Server: requests the live unlock counts for temporary stages. The server
 * replies with {@link SyncTemporaryCountsPacket}. Sent by the stage overview editor on
 * open and periodically while open.
 *
 * <p>{@code individualTarget} is the player the editor's picker is on, or null for "@a".
 * Individual temporary state is per player and only the picked one is worth sending — the
 * rows show a single count, and "@a" has no single count to show.
 */
public record RequestTemporaryCountsPacket(UUID individualTarget) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestTemporaryCountsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_temporary_counts"));

    public static final StreamCodec<FriendlyByteBuf, RequestTemporaryCountsPacket> STREAM_CODEC =
            StreamCodec.of(RequestTemporaryCountsPacket::encode, RequestTemporaryCountsPacket::decode);

    private static void encode(FriendlyByteBuf buffer, RequestTemporaryCountsPacket msg) {
        boolean hasTarget = msg.individualTarget != null;
        buffer.writeBoolean(hasTarget);
        if (hasTarget) buffer.writeUUID(msg.individualTarget);
    }

    private static RequestTemporaryCountsPacket decode(FriendlyByteBuf buffer) {
        return new RequestTemporaryCountsPacket(buffer.readBoolean() ? buffer.readUUID() : null);
    }

    public static void handle(RequestTemporaryCountsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

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

            PacketDistributor.sendToPlayer(player, new SyncTemporaryCountsPacket(
                    counts, activeTicks, target, individualCounts, individualActiveTicks));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
