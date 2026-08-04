package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.graph.GraphConfigCodec;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncGraphConfigPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Editor → server save of {@code graph.toml}, the mirror of {@link SyncGraphConfigPacket}.
 *
 * <p>Values are keyed by dotted toml path and applied through {@link GraphConfigCodec} with
 * validation on. There is deliberately no switch over key names: the equivalent list in
 * {@code SaveConfigPacket} has already drifted out of step with what it is supposed to cover.
 */
public record SaveGraphConfigPacket(Map<String, String> values) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveGraphConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_graph_config"));

    public static final StreamCodec<FriendlyByteBuf, SaveGraphConfigPacket> STREAM_CODEC =
            StreamCodec.of(SaveGraphConfigPacket::encode, SaveGraphConfigPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SaveGraphConfigPacket msg) {
        buffer.writeInt(msg.values.size());
        for (Map.Entry<String, String> entry : msg.values.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static SaveGraphConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SaveGraphConfigPacket(values);
    }

    public static void handle(SaveGraphConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            int applied = GraphConfigCodec.apply(msg.values, true);
            if (applied > 0) GraphConfig.GRAPH_SPEC.save();

            // Everyone, including the sender: the sender's own StageGraphConfig cache is
            // invalidated by the sync path, so it must not be skipped here. Sent even when
            // nothing applied — that is what puts the rejected fields back to their real values.
            PacketHandler.sendGraphConfigToAll(SyncGraphConfigPacket.fromServerConfig());

            int rejected = msg.values.size() - applied;
            if (rejected > 0) {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.graph_config_rejected.title",
                                "editor.historystages.toast.graph_config_rejected.message",
                                String.valueOf(rejected)),
                        player);
            } else {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.graph_config_saved.title",
                                "editor.historystages.toast.graph_config_saved.message"),
                        player);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
