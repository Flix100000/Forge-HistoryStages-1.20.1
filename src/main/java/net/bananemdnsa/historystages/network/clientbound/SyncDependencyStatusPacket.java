package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: Sends dependency check results for a stage. Carries {@code individual} so
 * the client can key {@link ClientDependencyCache} by tree — a global and an individual stage
 * may share an id.
 */
public class SyncDependencyStatusPacket {
    private static final Gson GSON = new GsonBuilder().create();

    private final String stageId;
    private final boolean individual;
    private final String resultJson;

    public SyncDependencyStatusPacket(String stageId, boolean individual, DependencyResult result) {
        this.stageId = stageId;
        this.individual = individual;
        this.resultJson = GSON.toJson(result);
    }

    private SyncDependencyStatusPacket(String stageId, boolean individual, String resultJson) {
        this.stageId = stageId;
        this.individual = individual;
        this.resultJson = resultJson;
    }

    public static void encode(SyncDependencyStatusPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.stageId);
        buf.writeBoolean(packet.individual);
        buf.writeUtf(packet.resultJson, 65536);
    }

    public static SyncDependencyStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncDependencyStatusPacket(buf.readUtf(), buf.readBoolean(), buf.readUtf(65536));
    }

    public static void handle(SyncDependencyStatusPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DependencyResult result = GSON.fromJson(packet.resultJson, DependencyResult.class);
            ClientDependencyCache.update(packet.stageId, packet.individual, result);
        });
        ctx.get().setPacketHandled(true);
    }
}
