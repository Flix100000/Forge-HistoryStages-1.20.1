package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;
import net.bananemdnsa.historystages.data.config.LocalConfigSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → client sync of {@code visual.toml}.
 *
 * <p>The visual settings used to be the one block the editor wrote purely locally, so an admin
 * tuning them changed nothing for anybody else. The server owns the file now and pushes it out the
 * same way {@link SyncGraphConfigPacket} pushes {@code graph.toml}: on login, and again whenever an
 * admin saves.
 *
 * <p>Keys are the dotted toml paths gathered by walking the spec, so a key added to
 * {@code Config.Client} syncs without anyone remembering to list it.
 *
 * <p>What this deliberately does not do: the received values travel into the client's in-memory
 * spec only. The player's own {@code visual.toml} on disk is never written, so the server's values
 * are borrowed for the session rather than kept. Restoring the local file's values when the player
 * disconnects is a separate concern and is not handled here.
 */
public record SyncVisualConfigPacket(Map<String, String> values) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncVisualConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_visual_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncVisualConfigPacket> STREAM_CODEC =
            StreamCodec.of(SyncVisualConfigPacket::encode, SyncVisualConfigPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncVisualConfigPacket msg) {
        buffer.writeInt(msg.values.size());
        for (Map.Entry<String, String> entry : msg.values.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static SyncVisualConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SyncVisualConfigPacket(values);
    }

    /** Snapshots every value in the visual spec, keyed by its dotted toml path. */
    public static SyncVisualConfigPacket fromServerConfig() {
        return new SyncVisualConfigPacket(ConfigSpecCodec.collect(Config.CLIENT_SPEC));
    }

    public static void handle(SyncVisualConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> apply(msg.values));
    }

    /** Writes the received values straight into the client's own spec objects — memory only. */
    public static void apply(Map<String, String> values) {
        // Before anything is overwritten, so the snapshot is the player's own file. Skipped on an
        // integrated server, where client and server share this very spec object: there is no
        // server copy to undo, and restoring later would roll back the host's own admin saves.
        if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) {
            LocalConfigSnapshot.rememberBeforeSync(Config.CLIENT_SPEC);
        }

        ConfigSpecCodec.apply(Config.CLIENT_SPEC, values, true, ConfigSpecCodec.NO_EXTRA_CHECK);

        // An open config editor holds a snapshot of the Client tab taken when it was built. Left
        // alone, it would re-send those pre-sync values on its next Save and undo whichever admin
        // saved first. Safe to call from here: unlike the common config's apply path, this one only
        // ever runs client-side.
        net.bananemdnsa.historystages.client.editor.ConfigEditorScreen.onVisualConfigSynced();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
