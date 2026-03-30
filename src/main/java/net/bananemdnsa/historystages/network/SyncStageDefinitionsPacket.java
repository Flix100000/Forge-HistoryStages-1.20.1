package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Syncs all stage definitions (not just unlocked stages) from server to client.
 * Sent on player login so the client knows which items/blocks/entities are locked.
 */
public record SyncStageDefinitionsPacket(Map<String, StageEntry> stages) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();

    public static final CustomPacketPayload.Type<SyncStageDefinitionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_stage_definitions"));

    public static final StreamCodec<FriendlyByteBuf, SyncStageDefinitionsPacket> STREAM_CODEC =
            StreamCodec.of(SyncStageDefinitionsPacket::encode, SyncStageDefinitionsPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncStageDefinitionsPacket msg) {
        String json = GSON.toJson(msg.stages);
        buffer.writeUtf(json, 262144);
    }

    private static SyncStageDefinitionsPacket decode(FriendlyByteBuf buffer) {
        String json = buffer.readUtf(262144);
        Map<String, StageEntry> stages = GSON.fromJson(json, MAP_TYPE);
        if (stages == null) stages = new HashMap<>();
        return new SyncStageDefinitionsPacket(stages);
    }

    public static void handle(SyncStageDefinitionsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StageManager.setStages(msg.stages);
            EditorDataCache.setStages(new HashMap<>(msg.stages));
            System.out.println("[HistoryStages] Received " + msg.stages.size() + " stage definitions from server.");
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
