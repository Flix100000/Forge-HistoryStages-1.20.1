package net.bananemdnsa.historystages.network.clientbound;
import net.bananemdnsa.historystages.network.serverbound.SaveConfigPacket;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.config.AddonConfigSections;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → Client packet that syncs the server's common config values to the client.
 * Sent on player login and after an admin saves the config via the editor.
 */
public record SyncConfigPacket(Map<String, String> configValues) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncConfigPacket> STREAM_CODEC =
            StreamCodec.of(SyncConfigPacket::encode, SyncConfigPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncConfigPacket msg) {
        buffer.writeInt(msg.configValues.size());
        for (Map.Entry<String, String> entry : msg.configValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static SyncConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SyncConfigPacket(values);
    }

    public static void handle(SyncConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Before anything is overwritten, so the snapshot is the player's own file. Skipped on
            // an integrated server, where client and server share this very spec object: there is
            // no server copy to undo, and restoring later would roll back the host's own saves.
            if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) {
                net.bananemdnsa.historystages.data.config.LocalConfigSnapshot
                        .rememberBeforeSync(Config.GAMEPLAY_SPEC);
            }

            SaveConfigPacket.applyCommonConfig(msg.configValues);

            // An open config editor holds a snapshot taken when it was built. Left alone, it would
            // re-send those pre-sync values on its next Save and undo whichever admin saved first.
            // The refresh belongs here rather than in applyCommonConfig, which also runs server-side.
            net.bananemdnsa.historystages.client.editor.ConfigEditorScreen.onCommonConfigSynced();
        });
    }

    /**
     * Creates a packet with all current server-side common config values.
     *
     * <p>Walks {@code GAMEPLAY_SPEC} rather than a hand-written key list, then adds the addon
     * sections, which are not in that spec — an addon keeps its own state behind the read/write
     * pair it registered, so the walk cannot see it. The two hand-maintained lists this replaced
     * kept drifting apart; at one point 28 keys the editor could change were never sent anywhere.
     */
    public static SyncConfigPacket fromServerConfig() {
        return new SyncConfigPacket(readServerConfig());
    }

    /**
     * The full server-side common map: spec values by dotted toml path, addon values by the wire
     * key {@link AddonConfigSections#wireKey} minted. Shared with the config editor, which needs
     * exactly this map to refresh an open screen after a sync — building it there separately is
     * how the addon rows would go stale while the rest of the screen updated.
     */
    public static Map<String, String> readServerConfig() {
        Map<String, String> values = ConfigSpecCodec.collect(Config.GAMEPLAY_SPEC);
        for (AddonConfigSections.CommonEntry entry : AddonConfigSections.commonEntries()) {
            values.put(entry.wireKey(), entry.read().get());
        }
        return values;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
