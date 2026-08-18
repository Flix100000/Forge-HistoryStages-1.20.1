package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphLayoutData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Discards one stage tree's manual layout so the auto-layout algorithm owns it again. This is a
 * plain delete of that tree's section in {@code graph_layout.json} — descriptions live in the
 * separate {@link net.bananemdnsa.historystages.data.graph.GraphStageData} file and survive.
 */
public class RearrangeGraphPacket {

    private final boolean individual;

    public RearrangeGraphPacket(boolean individual) {
        this.individual = individual;
    }

    public static void encode(RearrangeGraphPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.individual);
    }

    public static RearrangeGraphPacket decode(FriendlyByteBuf buffer) {
        return new RearrangeGraphPacket(buffer.readBoolean());
    }

    public static void handle(RearrangeGraphPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            GraphLayoutData.clear(msg.individual);
            StageManager.recomputeGraphLayout();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(
                            "editor.historystages.graph.toast.rearranged.title",
                            "editor.historystages.graph.toast.rearranged.message"),
                    player);
        });
        ctx.get().setPacketHandled(true);
    }
}
