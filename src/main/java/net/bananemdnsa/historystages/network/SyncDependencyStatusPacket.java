package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: Sends dependency check results for a stage.
 */
public record SyncDependencyStatusPacket(String stageId, String resultJson) implements CustomPacketPayload {
    private static final Gson GSON = new GsonBuilder().create();

    public static final CustomPacketPayload.Type<SyncDependencyStatusPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_dependency_status"));

    public static final StreamCodec<FriendlyByteBuf, SyncDependencyStatusPacket> STREAM_CODEC =
            StreamCodec.of(SyncDependencyStatusPacket::encode, SyncDependencyStatusPacket::decode);

    /** Convenience constructor that serialises the result to JSON. */
    public SyncDependencyStatusPacket(String stageId, DependencyResult result) {
        this(stageId, GSON.toJson(result));
    }

    private static void encode(FriendlyByteBuf buf, SyncDependencyStatusPacket packet) {
        buf.writeUtf(packet.stageId);
        buf.writeUtf(packet.resultJson, 65536);
    }

    private static SyncDependencyStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncDependencyStatusPacket(buf.readUtf(), buf.readUtf(65536));
    }

    public static void handle(SyncDependencyStatusPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            DependencyResult result = GSON.fromJson(packet.resultJson, DependencyResult.class);
            ClientDependencyCache.update(packet.stageId, result);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
