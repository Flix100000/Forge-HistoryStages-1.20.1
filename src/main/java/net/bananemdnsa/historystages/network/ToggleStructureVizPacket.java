package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.structure.ClusterDebugRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-issued: toggle the structure-cluster debug visualization for the requesting player.
 * Server stores the opt-in state and emits per-player particles outlining piece + cluster bounds.
 */
public record ToggleStructureVizPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleStructureVizPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "toggle_structure_viz"));

    public static final StreamCodec<FriendlyByteBuf, ToggleStructureVizPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {}, buf -> new ToggleStructureVizPacket());

    public static void handle(ToggleStructureVizPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            boolean nowEnabled = ClusterDebugRenderer.toggle(player.getUUID());
            player.sendSystemMessage(Component.literal(nowEnabled
                    ? "§aStructure cluster visualization §lENABLED§a — particles will appear when near locked structures."
                    : "§7Structure cluster visualization §lDISABLED§7."));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
