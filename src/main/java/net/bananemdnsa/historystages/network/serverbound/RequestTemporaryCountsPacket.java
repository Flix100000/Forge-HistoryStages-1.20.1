package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.clientbound.SyncTemporaryCountsPacket;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Client → Server: requests the live unlock counts for global temporary stages.
 * Lightweight (no payload); the server replies with {@link SyncTemporaryCountsPacket}.
 * Sent by the stage overview editor on open and periodically while open.
 */
public record RequestTemporaryCountsPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestTemporaryCountsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_temporary_counts"));

    public static final StreamCodec<FriendlyByteBuf, RequestTemporaryCountsPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {}, buf -> new RequestTemporaryCountsPacket());

    public static void handle(RequestTemporaryCountsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            Map<String, Integer> counts = new HashMap<>();
            Map<String, Long> activeTicks = new HashMap<>();
            var temp = net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(player.serverLevel());
            for (var e : StageManager.getStages().entrySet()) {
                if (e.getValue().getMode() == StageMode.TEMPORARY) {
                    String id = e.getKey();
                    counts.put(id, temp.getGlobalCount(id));
                    long remaining = temp.globalActiveTicks(id);
                    if (remaining > 0) activeTicks.put(id, remaining);
                }
            }
            PacketDistributor.sendToPlayer(player, new SyncTemporaryCountsPacket(counts, activeTicks));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
