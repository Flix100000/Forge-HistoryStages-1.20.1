package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SaveConfigPacket {
    private final Map<String, String> configValues;
    private final boolean isClient; // true = client config, false = common config

    public SaveConfigPacket(Map<String, String> configValues, boolean isClient) {
        this.configValues = configValues;
        this.isClient = isClient;
    }

    public static void encode(SaveConfigPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.isClient);
        buffer.writeInt(msg.configValues.size());
        for (Map.Entry<String, String> entry : msg.configValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static SaveConfigPacket decode(FriendlyByteBuf buffer) {
        boolean isClient = buffer.readBoolean();
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SaveConfigPacket(values, isClient);
    }

    public static void handle(SaveConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            // Apply common config values on the server
            if (!msg.isClient) {
                applyCommonConfig(msg.configValues);
                // Force Forge to persist the TOML file to disk
                Config.COMMON_SPEC.save();
                // Sync updated config to all connected clients
                PacketHandler.sendConfigToAll(SyncConfigPacket.fromServerConfig());
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.config_saved.title",
                                "editor.historystages.toast.config_saved.message"),
                        player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Applies wire values to the common config. Runs on the server when an admin saves the editor,
     * and on the client when the server syncs back.
     * <p>
     * The per-key handling lives in {@link CommonConfigSync}, which also produces the synced map —
     * one list for both directions. Before that, this was a switch and the sync packet was a
     * separate list of puts; keys kept being added here and forgotten there, so admins could change
     * a setting the server saved but no client ever heard about.
     */
    public static void applyCommonConfig(Map<String, String> values) {
        CommonConfigSync.applyAll(values);
    }
}
