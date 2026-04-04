package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Syncs all stage definitions (not just unlocked stages) from server to client.
 * Sent on player login so the client knows which items/blocks/entities are locked.
 */
public class SyncStageDefinitionsPacket {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();

    private final Map<String, StageEntry> stages;
    private final Map<String, StageEntry> individualStages;

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages) {
        this(stages, StageManager.getIndividualStages());
    }

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages) {
        this.stages = stages;
        this.individualStages = individualStages;
    }

    public static void encode(SyncStageDefinitionsPacket msg, FriendlyByteBuf buffer) {
        String json = GSON.toJson(msg.stages);
        buffer.writeUtf(json, 262144); // 256KB max
        String individualJson = GSON.toJson(msg.individualStages);
        buffer.writeUtf(individualJson, 262144);
    }

    public static SyncStageDefinitionsPacket decode(FriendlyByteBuf buffer) {
        String json = buffer.readUtf(262144);
        Map<String, StageEntry> stages = GSON.fromJson(json, MAP_TYPE);
        if (stages == null) stages = new HashMap<>();
        String individualJson = buffer.readUtf(262144);
        Map<String, StageEntry> individualStages = GSON.fromJson(individualJson, MAP_TYPE);
        if (individualStages == null) individualStages = new HashMap<>();
        return new SyncStageDefinitionsPacket(stages, individualStages);
    }

    public static void handle(SyncStageDefinitionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Replace client-side stage definitions with the server's data
            StageManager.setStages(msg.stages);
            StageManager.setIndividualStages(msg.individualStages);
            // Keep editor cache in sync so open editors always show current data
            EditorDataCache.setStages(new HashMap<>(msg.stages));
            System.out.println("[HistoryStages] Received " + msg.stages.size() + " stage definitions + "
                    + msg.individualStages.size() + " individual stage definitions from server.");
        });
        ctx.get().setPacketHandled(true);
    }
}
