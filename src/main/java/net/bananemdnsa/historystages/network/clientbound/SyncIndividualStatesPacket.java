package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientPlayerStageCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server → Client: the individual-stage set of every online player, for the stage
 * editor's player picker. Stored in {@link ClientPlayerStageCache}.
 */
public record SyncIndividualStatesPacket(Map<UUID, Set<String>> playerStages) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncIndividualStatesPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_individual_states"));

    public static final StreamCodec<FriendlyByteBuf, SyncIndividualStatesPacket> STREAM_CODEC =
            StreamCodec.of(SyncIndividualStatesPacket::encode, SyncIndividualStatesPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncIndividualStatesPacket msg) {
        buffer.writeVarInt(msg.playerStages.size());
        for (Map.Entry<UUID, Set<String>> e : msg.playerStages.entrySet()) {
            buffer.writeUUID(e.getKey());
            buffer.writeVarInt(e.getValue().size());
            for (String stage : e.getValue()) buffer.writeUtf(stage);
        }
    }

    private static SyncIndividualStatesPacket decode(FriendlyByteBuf buffer) {
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

    public static void handle(SyncIndividualStatesPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPlayerStageCache.set(msg.playerStages));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
