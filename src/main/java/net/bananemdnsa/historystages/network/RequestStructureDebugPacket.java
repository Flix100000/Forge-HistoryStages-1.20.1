package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.events.StructureLockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-issued request: server collects structure holders at the player's position
 * and sends each line back as a system message to that player only.
 * Used by the client-side {@code /history debug structure} command so its output
 * stays purely local to the executor without going through Brigadier broadcast paths.
 */
public record RequestStructureDebugPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestStructureDebugPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_structure_debug"));

    public static final StreamCodec<FriendlyByteBuf, RequestStructureDebugPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {}, buf -> new RequestStructureDebugPacket());

    public static void handle(RequestStructureDebugPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
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
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
