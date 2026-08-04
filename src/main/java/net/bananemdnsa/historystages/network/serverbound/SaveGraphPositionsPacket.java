package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.graph.GraphLayoutData;
import net.bananemdnsa.historystages.data.graph.GraphPos;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Freezes one stage tree's graph layout: the author dragged something, so the whole current
 * position map is written out wholesale. Sent both for the very first drag in an unfrozen tree
 * and for every later drag in an already-frozen one — the client always sends the complete map,
 * never just the moved node, so the handler never needs to merge.
 *
 * <p>The map rides the wire as the same JSON {@link GraphLayoutData#toJson} writes to
 * {@code graph_layout.json} — built as a one-tree snapshot (the other tree left empty) so file
 * and wire share one serialiser and cannot disagree.
 */
public record SaveGraphPositionsPacket(boolean individual, Map<String, GraphPos> positions) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveGraphPositionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_graph_positions"));

    public static final StreamCodec<FriendlyByteBuf, SaveGraphPositionsPacket> STREAM_CODEC =
            StreamCodec.of(SaveGraphPositionsPacket::encode, SaveGraphPositionsPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SaveGraphPositionsPacket msg) {
        buffer.writeBoolean(msg.individual);
        // The flags do not travel: this packet always means "freeze this one tree", and the
        // handler is what decides that. Only the positions matter on the wire.
        GraphLayoutData.Snapshot oneTree =
                GraphLayoutData.Snapshot.empty().withPositions(msg.individual, msg.positions);
        buffer.writeUtf(GraphLayoutData.toJson(oneTree), 65536);
    }

    private static SaveGraphPositionsPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        GraphLayoutData.Snapshot oneTree = GraphLayoutData.fromJson(buffer.readUtf(65536));
        return new SaveGraphPositionsPacket(individual, oneTree.tree(individual));
    }

    public static void handle(SaveGraphPositionsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            // Stage ids are file names; drop anything a hand-edited client or stale save could
            // have smuggled in rather than writing it into graph_layout.json.
            Map<String, GraphPos> validated = new HashMap<>();
            for (Map.Entry<String, GraphPos> entry : msg.positions.entrySet()) {
                if (StagePaths.isValidSegment(entry.getKey())) {
                    validated.put(entry.getKey(), entry.getValue());
                }
            }

            GraphLayoutData.freeze(msg.individual, validated);
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(
                            "editor.historystages.graph.toast.positions_saved.title",
                            "editor.historystages.graph.toast.positions_saved.message"),
                    player);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
