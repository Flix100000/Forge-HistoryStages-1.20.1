package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.CommonConfigSync;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record SaveConfigPacket(Map<String, String> configValues, boolean isClient) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_config"));

    public static final StreamCodec<FriendlyByteBuf, SaveConfigPacket> STREAM_CODEC =
            StreamCodec.of(SaveConfigPacket::encode, SaveConfigPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SaveConfigPacket msg) {
        buffer.writeBoolean(msg.isClient);
        buffer.writeInt(msg.configValues.size());
        for (Map.Entry<String, String> entry : msg.configValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static SaveConfigPacket decode(FriendlyByteBuf buffer) {
        boolean isClient = buffer.readBoolean();
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SaveConfigPacket(values, isClient);
    }

    public static void handle(SaveConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            if (!msg.isClient) {
                applyCommonConfig(msg.configValues);
                Config.COMMON_SPEC.save();
                PacketHandler.sendConfigToAll(SyncConfigPacket.fromServerConfig());
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.config_saved.title",
                                "editor.historystages.toast.config_saved.message"),
                        player);
            }
        });
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

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
