package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sets (or clears, when {@code description} is blank) one stage's hand-written graph description
 * in {@code graph_stages.json}. Independent of the layout file so re-arranging the layout never
 * costs the author these texts.
 */
public record SaveStageGraphInfoPacket(String stageId, boolean individual, String description) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveStageGraphInfoPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_stage_graph_info"));

    public static final StreamCodec<FriendlyByteBuf, SaveStageGraphInfoPacket> STREAM_CODEC =
            StreamCodec.of(SaveStageGraphInfoPacket::encode, SaveStageGraphInfoPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SaveStageGraphInfoPacket msg) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.individual);
        // A null description (clearing the text) is a valid input for withDescription, but
        // FriendlyByteBuf.writeUtf requires a non-null String, hence the empty-string fallback.
        buffer.writeUtf(msg.description != null ? msg.description : "", 4096);
    }

    private static SaveStageGraphInfoPacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        boolean individual = buffer.readBoolean();
        String description = buffer.readUtf(4096);
        return new SaveStageGraphInfoPacket(stageId, individual, description);
    }

    public static void handle(SaveStageGraphInfoPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            // Stage ids are file names; a value that fails this check must never reach
            // graph_stages.json.
            if (!StagePaths.isValidSegment(msg.stageId)) return;

            GraphStageData.set(GraphStageData.get().withDescription(msg.stageId, msg.individual, msg.description));
            GraphStageData.save();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(
                            "editor.historystages.graph.toast.info_saved.title",
                            "editor.historystages.graph.toast.info_saved.message",
                            msg.stageId),
                    player);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
