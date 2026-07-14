package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.events.lock.StructureLockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-issued request: server collects structure holders at the player's position
 * and sends each line back as a system message to that player only.
 * Used by the client-side {@code /history debug structure} command so its output
 * stays purely local to the executor without going through Brigadier broadcast paths.
 */
public class RequestStructureDebugPacket {

    public RequestStructureDebugPacket() {}

    public static void encode(RequestStructureDebugPacket msg, FriendlyByteBuf buffer) {
    }

    public static RequestStructureDebugPacket decode(FriendlyByteBuf buffer) {
        return new RequestStructureDebugPacket();
    }

    public static void handle(RequestStructureDebugPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            BlockPos pos = player.blockPosition();
            var holders = StructureLockHandler.collectStructureHoldersAt(player.serverLevel(), pos);

            player.sendSystemMessage(Component.literal(
                    "§6--- Structures at §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " §6---"));

            if (holders.isEmpty()) {
                player.sendSystemMessage(Component.literal("  §7(not inside any structure)"));
                return;
            }

            for (var h : holders) {
                String id = h.unwrapKey().map(k -> k.location().toString()).orElse("<unknown>");
                player.sendSystemMessage(Component.literal("  §8• §f" + id));
                h.tags().forEach(tag -> player.sendSystemMessage(
                        Component.literal("      §8↳ §b#" + tag.location())));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
