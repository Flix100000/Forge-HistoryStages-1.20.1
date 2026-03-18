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

public class EditorSyncPacket {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();

    private final Map<String, StageEntry> stages;

    public EditorSyncPacket(Map<String, StageEntry> stages) {
        this.stages = stages;
    }

    public static void encode(EditorSyncPacket msg, FriendlyByteBuf buffer) {
        String json = GSON.toJson(msg.stages);
        buffer.writeUtf(json, 262144); // 256KB max
    }

    public static EditorSyncPacket decode(FriendlyByteBuf buffer) {
        String json = buffer.readUtf(262144);
        Map<String, StageEntry> stages = GSON.fromJson(json, MAP_TYPE);
        if (stages == null) stages = new HashMap<>();
        return new EditorSyncPacket(stages);
    }

    public static void handle(EditorSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Update client-side stage data for the editor
            // We store this in a temporary holder that the editor screens can access
            EditorDataCache.setStages(msg.stages);
        });
        ctx.get().setPacketHandled(true);
    }
}
