package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.structure.ClusterDebugRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-issued: toggles the structure-cluster debug visualization for the requesting player.
 * Server stores the opt-in state in {@link ClusterDebugRenderer} and emits per-player particles
 * outlining piece + cluster bounds while the toggle is on.
 */
public class ToggleStructureVizPacket {

    public ToggleStructureVizPacket() {}

    public static void encode(ToggleStructureVizPacket msg, FriendlyByteBuf buffer) {
    }

    public static ToggleStructureVizPacket decode(FriendlyByteBuf buffer) {
        return new ToggleStructureVizPacket();
    }

    public static void handle(ToggleStructureVizPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;
            boolean nowEnabled = ClusterDebugRenderer.toggle(player.getUUID());
            player.sendSystemMessage(Component.literal(nowEnabled
                    ? "§aStructure cluster visualization §lENABLED§a — particles will appear when near locked structures."
                    : "§7Structure cluster visualization §lDISABLED§7."));
        });
        ctx.get().setPacketHandled(true);
    }
}
