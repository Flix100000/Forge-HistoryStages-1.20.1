package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncDependencyStatusPayload(String stageId, String resultJson) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();

    public static final Type<SyncDependencyStatusPayload> TYPE = new Type<>(HistoryStages.id("sync_dependency_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDependencyStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncDependencyStatusPayload::stageId,
            ByteBufCodecs.STRING_UTF8, SyncDependencyStatusPayload::resultJson,
            SyncDependencyStatusPayload::new
    );

    public static SyncDependencyStatusPayload of(String stageId, DependencyResult result) {
        return new SyncDependencyStatusPayload(stageId, GSON.toJson(result));
    }

    public DependencyResult decode() {
        return GSON.fromJson(resultJson, DependencyResult.class);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
