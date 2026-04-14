package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: Sends dependency check results for a stage.
 */
public class SyncDependencyStatusPacket {
    private static final Gson GSON = new GsonBuilder().create();

    private final String stageId;
    private final String resultJson;

    public SyncDependencyStatusPacket(String stageId, DependencyResult result) {
        this.stageId = stageId;
        this.resultJson = GSON.toJson(result);
    }

    private SyncDependencyStatusPacket(String stageId, String resultJson) {
        this.stageId = stageId;
        this.resultJson = resultJson;
    }

    public static void encode(SyncDependencyStatusPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.stageId);
        buf.writeUtf(packet.resultJson, 65536);
    }

    public static SyncDependencyStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncDependencyStatusPacket(buf.readUtf(), buf.readUtf(65536));
    }

    public static void handle(SyncDependencyStatusPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DependencyResult result = GSON.fromJson(packet.resultJson, DependencyResult.class);
            ClientDependencyCache.update(packet.stageId, result);
        });
        ctx.get().setPacketHandled(true);
    }
}
