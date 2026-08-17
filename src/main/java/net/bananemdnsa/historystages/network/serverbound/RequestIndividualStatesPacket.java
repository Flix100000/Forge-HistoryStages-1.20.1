package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server: asks for every online player's individual-stage set. Lightweight
 * (no payload); the server replies with {@link SyncIndividualStatesPacket}. Sent by
 * the stage overview editor on open and on its ~1s poll tick.
 */
public class RequestIndividualStatesPacket {

    public RequestIndividualStatesPacket() {}

    public static void encode(RequestIndividualStatesPacket msg, FriendlyByteBuf buffer) {
        // No data needed
    }

    public static RequestIndividualStatesPacket decode(FriendlyByteBuf buffer) {
        return new RequestIndividualStatesPacket();
    }

    public static void handle(RequestIndividualStatesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            IndividualStageData data = IndividualStageData.get(player.serverLevel());
            Map<UUID, Set<String>> states = new HashMap<>();
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                states.put(online.getUUID(), new HashSet<>(data.getUnlockedStages(online.getUUID())));
            }
            PacketHandler.INSTANCE.reply(new SyncIndividualStatesPacket(states), ctx.get());
        });
        ctx.get().setPacketHandled(true);
    }
}
