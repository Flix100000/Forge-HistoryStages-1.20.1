package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sets (or clears, when {@code description} is blank) one stage's hand-written graph description
 * in {@code graph_stages.json}. Independent of the layout file so re-arranging the layout never
 * costs the author these texts.
 */
public class SaveStageGraphInfoPacket {

    private final String stageId;
    private final boolean individual;
    private final String description;

    public SaveStageGraphInfoPacket(String stageId, boolean individual, String description) {
        this.stageId = stageId;
        this.individual = individual;
        this.description = description;
    }

    public static void encode(SaveStageGraphInfoPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.individual);
        // A null description (clearing the text) is a valid input for withDescription, but
        // FriendlyByteBuf.writeUtf requires a non-null String, hence the empty-string fallback.
        buffer.writeUtf(msg.description != null ? msg.description : "", 4096);
    }

    public static SaveStageGraphInfoPacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        boolean individual = buffer.readBoolean();
        String description = buffer.readUtf(4096);
        return new SaveStageGraphInfoPacket(stageId, individual, description);
    }

    public static void handle(SaveStageGraphInfoPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
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
        ctx.get().setPacketHandled(true);
    }
}
