package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.client.editor.graph.StageGraphConfig;
import net.bananemdnsa.historystages.data.graph.GraphConfigCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server -&gt; client sync of {@code graph.toml}.
 *
 * <p>The values are gathered by walking the spec itself, so a key added to {@code GraphConfig}
 * syncs without anyone remembering to list it.
 *
 * <p>Keys are the dotted toml paths ({@code style.global.unlocked.shape}), not the invented flat
 * names {@code SyncConfigPacket} uses. Flat names exist there because leaf names repeat across
 * categories; a path is unambiguous by construction, and this packet has no legacy wire format to
 * stay compatible with.
 */
public class SyncGraphConfigPacket {

    private final Map<String, String> values;

    public SyncGraphConfigPacket(Map<String, String> values) {
        this.values = values;
    }

    public static void encode(SyncGraphConfigPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.values.size());
        for (Map.Entry<String, String> entry : msg.values.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static SyncGraphConfigPacket decode(FriendlyByteBuf buffer) {
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

    public static void handle(SyncGraphConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> apply(msg.values));
        ctx.get().setPacketHandled(true);
    }

    /** Writes the received values straight into the client's own spec objects. */
    public static void apply(Map<String, String> values) {
        GraphConfigCodec.apply(values, true);

        // graph.toml changed under it — every previously resolved node style is stale.
        StageGraphConfig.invalidateCache();

        // An open config editor holds a snapshot of the Graph tab taken when it was built. Left
        // alone, it would re-send those pre-sync values on its next Save and undo whichever admin
        // saved first. The screen is client-only, so it is reached through DistExecutor rather than
        // named here — a packet class is loaded on the dedicated server too, and calling straight
        // into client-only code from here is what forced the 5.6.1 hotfix on the neoforge branch.
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> net.bananemdnsa.historystages.client.editor.ConfigEditorScreen
                        .onGraphConfigSynced());
    }
}
