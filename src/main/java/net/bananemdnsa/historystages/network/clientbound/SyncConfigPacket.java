package net.bananemdnsa.historystages.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server → Client packet that syncs the server's common config values to the client.
 * Sent on player login and after an admin saves the config via the editor.
 */
public class SyncConfigPacket {
    private final Map<String, String> configValues;

    public SyncConfigPacket(Map<String, String> configValues) {
        this.configValues = configValues;
    }

    public static void encode(SyncConfigPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.configValues.size());
        for (Map.Entry<String, String> entry : msg.configValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static SyncConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SyncConfigPacket(values);
    }

    public static void handle(SyncConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Apply server's config values on the client
            SaveConfigPacket.applyCommonConfig(msg.configValues);

            // An open config editor holds a snapshot of the Common tab taken when it was built.
            // Left alone, it would re-send those pre-sync values on its next Save and undo
            // whichever admin saved first. The screen is client-only, so it is reached through
            // DistExecutor rather than named here — a packet class is loaded on the dedicated
            // server too.
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> net.bananemdnsa.historystages.client.editor.ConfigEditorScreen
                            .onCommonConfigSynced());
        });
        ctx.get().setPacketHandled(true);
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
}
