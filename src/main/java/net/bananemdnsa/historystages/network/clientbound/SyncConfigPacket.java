package net.bananemdnsa.historystages.network.clientbound;
import net.bananemdnsa.historystages.network.CommonConfigSync;
import net.bananemdnsa.historystages.network.serverbound.SaveConfigPacket;

import net.bananemdnsa.historystages.HistoryStages;
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
            SaveConfigPacket.applyCommonConfig(msg.configValues);

            // An open config editor holds a snapshot taken when it was built. Left alone, it would
            // re-send those pre-sync values on its next Save and undo whichever admin saved first.
            // The refresh belongs here rather than in applyCommonConfig, which also runs server-side.
            net.bananemdnsa.historystages.client.editor.ConfigEditorScreen.onCommonConfigSynced();
        });
    }

    /**
     * Creates a packet with all current server-side common config values.
     * <p>
     * The key list lives in {@link CommonConfigSync}, shared with the apply path, so a setting
     * can no longer be saveable but unsyncable.
     */
    public static SyncConfigPacket fromServerConfig() {
        return new SyncConfigPacket(CommonConfigSync.readAll());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
