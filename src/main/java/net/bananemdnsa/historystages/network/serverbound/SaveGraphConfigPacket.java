package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.data.graph.GraphConfigCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Editor → server save of {@code graph.toml}, the mirror of {@link SyncGraphConfigPacket}.
 *
 * <p>Values are keyed by dotted toml path and applied through {@link GraphConfigCodec} with
 * validation on. There is deliberately no switch over key names: the equivalent list in
 * {@code SaveConfigPacket} has already drifted out of step with what it is supposed to cover.
 */
public class SaveGraphConfigPacket {

    private final Map<String, String> values;

    public SaveGraphConfigPacket(Map<String, String> values) {
        this.values = values;
    }

    public static void encode(SaveGraphConfigPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.values.size());
        for (Map.Entry<String, String> entry : msg.values.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static SaveGraphConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SaveGraphConfigPacket(values);
    }

    public static void handle(SaveGraphConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

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
        ctx.get().setPacketHandled(true);
    }
}
