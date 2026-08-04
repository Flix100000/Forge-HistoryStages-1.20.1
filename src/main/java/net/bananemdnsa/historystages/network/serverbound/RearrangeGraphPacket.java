package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphLayoutData;
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
 * Discards one stage tree's manual layout so the auto-layout algorithm owns it again. This is a
 * plain delete of that tree's section in {@code graph_layout.json} — descriptions live in the
 * separate {@link net.bananemdnsa.historystages.data.graph.GraphStageData} file and survive.
 */
public record RearrangeGraphPacket(boolean individual) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RearrangeGraphPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "rearrange_graph"));

    public static final StreamCodec<FriendlyByteBuf, RearrangeGraphPacket> STREAM_CODEC =
            StreamCodec.of(RearrangeGraphPacket::encode, RearrangeGraphPacket::decode);

    private static void encode(FriendlyByteBuf buffer, RearrangeGraphPacket msg) {
        buffer.writeBoolean(msg.individual);
    }

    private static RearrangeGraphPacket decode(FriendlyByteBuf buffer) {
        return new RearrangeGraphPacket(buffer.readBoolean());
    }

    public static void handle(RearrangeGraphPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            GraphLayoutData.clear(msg.individual);
            StageManager.recomputeGraphLayout();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(
                            "editor.historystages.graph.toast.rearranged.title",
                            "editor.historystages.graph.toast.rearranged.message"),
                    player);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
