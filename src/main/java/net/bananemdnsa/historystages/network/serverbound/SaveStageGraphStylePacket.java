package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.graph.GraphConfigEntries;
import net.bananemdnsa.historystages.data.graph.GraphKey;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.data.graph.NodeState;
import net.bananemdnsa.historystages.data.graph.StageStyle;
import net.bananemdnsa.historystages.data.graph.StageStyleFields;
import net.bananemdnsa.historystages.data.graph.StageStyleValidator;
import net.bananemdnsa.historystages.data.graph.StateStyles;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sets (or clears) one stage's node-style override in {@code graph_stages.json}.
 *
 * <p>The payload is the stage's own JSON fragment — the {@code style} and {@code styles} halves
 * of a {@link GraphStageData.Entry} — rather than a field list, so the wire form and the file
 * form cannot drift apart. Shaped like {@link SaveStageGraphInfoPacket}, which writes the other
 * half of the same file.
 */
public record SaveStageGraphStylePacket(String stageId, boolean individual, String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveStageGraphStylePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_stage_graph_style"));

    public static final StreamCodec<FriendlyByteBuf, SaveStageGraphStylePacket> STREAM_CODEC =
            StreamCodec.of(SaveStageGraphStylePacket::encode, SaveStageGraphStylePacket::decode);

    private static void encode(FriendlyByteBuf buffer, SaveStageGraphStylePacket msg) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.individual);
        buffer.writeUtf(msg.json != null ? msg.json : "{}", 8192);
    }

    private static SaveStageGraphStylePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        boolean individual = buffer.readBoolean();
        String json = buffer.readUtf(8192);
        return new SaveStageGraphStylePacket(stageId, individual, json);
    }

    public static void handle(SaveStageGraphStylePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            // Stage ids are file names; a value that fails this check must never reach
            // graph_stages.json.
            if (!StagePaths.isValidSegment(msg.stageId)) return;

            GraphStageData.Entry incoming = GraphStageData.entryFromJson(msg.json);
            GraphStageData.Entry stored =
                    GraphStageData.get().tree(msg.individual).get(msg.stageId);
            GraphStageData.Entry cleaned = sanitize(incoming, stored, msg.individual);

            GraphStageData.set(GraphStageData.get().withStyle(msg.stageId, msg.individual, cleaned));
            GraphStageData.save();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(
                            "editor.historystages.graph.style.toast.saved.title",
                            "editor.historystages.graph.style.toast.saved.message",
                            msg.stageId),
                    player);
        });
    }

    /**
     * Every block checked against the spec of the block it will be layered onto. The client
     * bounds its own inputs, so this only ever fires for a modified one — which is exactly the
     * case that must not be able to write nonsense into a file every player then loads.
     */
    private static GraphStageData.Entry sanitize(GraphStageData.Entry incoming,
                                                 GraphStageData.Entry stored, boolean individual) {
        String collection = individual ? "individual" : "global";
        GraphStageData.Entry out = new GraphStageData.Entry();

        // The all-states block has no state of its own; unlocked's spec supplies the types and
        // ranges, which are identical across the three blocks.
        List<GraphKey> allStatesKeys = GraphConfigEntries.styleKeys(collection, "unlocked");
        out.style = StageStyleValidator.sanitize(incoming.style, allStatesKeys);
        carryOverHidden(stored == null ? null : stored.style, out.style, allStatesKeys);

        StateStyles storedStates = stored == null ? null : stored.styles;
        if (incoming.styles != null || storedStates != null) {
            StateStyles states = new StateStyles();
            for (NodeState state : NodeState.values()) {
                List<GraphKey> keys = GraphConfigEntries.styleKeys(
                        collection, state.name().toLowerCase(Locale.ROOT));
                StageStyle checked = StageStyleValidator.sanitize(
                        incoming.styles == null ? null : incoming.styles.get(state), keys);
                carryOverHidden(storedStates == null ? null : storedStates.get(state), checked, keys);
                states.set(state, checked.isEmpty() ? null : checked);
            }
            out.styles = states;
        }
        return out;
    }

    /**
     * Copies the leaves {@code keys} has no entry for out of what was already in the file.
     *
     * <p>The editor may not destroy what it does not show. {@code cornerRadius} is kept out of
     * the editable set by {@code GraphConfigEntries.HIDDEN_LEAVES} and so never reaches a row;
     * without this, a value an author wrote there by hand would be gone the first time anyone
     * opened that stage's style screen and pressed Save.
     */
    private static void carryOverHidden(StageStyle stored, StageStyle target, List<GraphKey> keys) {
        if (stored == null) return;
        Set<String> editable = new HashSet<>();
        for (GraphKey key : keys) editable.add(key.leaf());

        for (String leaf : StageStyleFields.LEAVES) {
            if (editable.contains(leaf)) continue;
            String kept = StageStyleFields.get(stored, leaf);
            if (kept != null) StageStyleFields.set(target, leaf, kept);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
