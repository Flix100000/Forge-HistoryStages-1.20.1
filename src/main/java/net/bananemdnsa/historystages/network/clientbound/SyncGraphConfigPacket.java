package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphConfig;
import net.bananemdnsa.historystages.data.graph.GraphConfigCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → client sync of {@code graph.toml}.
 *
 * <p>The values are gathered by walking the spec itself, so a key added to {@code GraphConfig}
 * syncs without anyone remembering to list it.
 *
 * <p>Keys are the dotted toml paths ({@code style.global.unlocked.shape}), not the invented flat
 * names {@link SyncConfigPacket} uses. Flat names exist there because leaf names repeat across
 * categories; a path is unambiguous by construction, and this packet has no legacy wire format to
 * stay compatible with.
 */
public record SyncGraphConfigPacket(Map<String, String> values) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncGraphConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_graph_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncGraphConfigPacket> STREAM_CODEC =
            StreamCodec.of(SyncGraphConfigPacket::encode, SyncGraphConfigPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncGraphConfigPacket msg) {
        buffer.writeInt(msg.values.size());
        for (Map.Entry<String, String> entry : msg.values.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static SyncGraphConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SyncGraphConfigPacket(values);
    }

    /** Snapshots every value in the graph spec, keyed by its dotted toml path. */
    public static SyncGraphConfigPacket fromServerConfig() {
        return new SyncGraphConfigPacket(GraphConfigCodec.collect());
    }

    public static void handle(SyncGraphConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> apply(msg.values));
    }

    /** Writes the received values straight into the client's own spec objects. */
    public static void apply(Map<String, String> values) {
        GraphConfigCodec.apply(values, true);

        // graph.toml changed under it — every previously resolved node style is stale.
        StageGraphConfig.invalidateCache();

        // An open config editor holds a snapshot of the Graph tab taken when it was built. Left
        // alone, it would re-send those pre-sync values on its next Save and undo whichever admin
        // saved first. Safe to call from here: unlike the common config's apply path, this one only
        // ever runs client-side.
        net.bananemdnsa.historystages.client.editor.ConfigEditorScreen.onGraphConfigSynced();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
