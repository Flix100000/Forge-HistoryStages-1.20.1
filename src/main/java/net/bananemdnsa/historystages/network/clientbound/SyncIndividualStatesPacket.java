package net.bananemdnsa.historystages.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client: the individual-stage set of every online player, for the stage
 * editor's player picker. Stored in {@code ClientPlayerStageCache}.
 *
 * <p>That cache is client-only, so it is never named in this class's signatures or
 * bodies — only inside the {@link DistExecutor} supplier. A packet class is loaded on
 * the dedicated server too, and a direct reference would drag a client class onto it.
 */
public class SyncIndividualStatesPacket {

    private final Map<UUID, Set<String>> playerStages;

    public SyncIndividualStatesPacket(Map<UUID, Set<String>> playerStages) {
        this.playerStages = playerStages != null ? playerStages : new HashMap<>();
    }

    public static void encode(SyncIndividualStatesPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.playerStages.size());
        for (Map.Entry<UUID, Set<String>> e : msg.playerStages.entrySet()) {
            buffer.writeUUID(e.getKey());
            buffer.writeVarInt(e.getValue().size());
            for (String stage : e.getValue()) buffer.writeUtf(stage);
        }
    }

    public static SyncIndividualStatesPacket decode(FriendlyByteBuf buffer) {
        int players = buffer.readVarInt();
        Map<UUID, Set<String>> map = new HashMap<>();
        for (int i = 0; i < players; i++) {
            UUID uuid = buffer.readUUID();
            int n = buffer.readVarInt();
            Set<String> stages = new HashSet<>();
            for (int j = 0; j < n; j++) stages.add(buffer.readUtf());
            map.put(uuid, stages);
        }
        return new SyncIndividualStatesPacket(map);
    }

    public static void handle(SyncIndividualStatesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.bananemdnsa.historystages.client.cache.ClientPlayerStageCache.set(msg.playerStages)));
        ctx.get().setPacketHandled(true);
    }
}
