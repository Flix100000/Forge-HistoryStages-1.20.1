package net.bananemdnsa.historystages.network.clientbound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: Sends dependency check results for a stage. Carries {@code individual} so
 * the client can key {@link ClientDependencyCache} by tree — a global and an individual stage
 * may share an id.
 */
public record SyncDependencyStatusPacket(String stageId, boolean individual, String resultJson)
        implements CustomPacketPayload {
    private static final Gson GSON = new GsonBuilder().create();

    public static final CustomPacketPayload.Type<SyncDependencyStatusPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_dependency_status"));

    public static final StreamCodec<FriendlyByteBuf, SyncDependencyStatusPacket> STREAM_CODEC =
            StreamCodec.of(SyncDependencyStatusPacket::encode, SyncDependencyStatusPacket::decode);

    /** Convenience constructor that serialises the result to JSON. */
    public SyncDependencyStatusPacket(String stageId, boolean individual, RequirementResult result) {
        this(stageId, individual, GSON.toJson(result));
    }

    private static void encode(FriendlyByteBuf buf, SyncDependencyStatusPacket packet) {
        buf.writeUtf(packet.stageId);
        buf.writeBoolean(packet.individual);
        buf.writeUtf(packet.resultJson, 65536);
    }

    private static SyncDependencyStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncDependencyStatusPacket(buf.readUtf(), buf.readBoolean(), buf.readUtf(65536));
    }

    public static void handle(SyncDependencyStatusPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            RequirementResult result = GSON.fromJson(packet.resultJson, RequirementResult.class);
            ClientDependencyCache.update(packet.stageId, packet.individual, result);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
