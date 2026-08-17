package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Syncs all stage definitions (not just unlocked stages) from server to client.
 * Sent on player login so the client knows which items/blocks/entities are locked.
 *
 * <p>Also carries the folder layout of both trees (stage id → folder path, and every
 * folder including empty ones), because the client never sees the config files on a
 * dedicated server — the folder tree only reaches it through this sync.
 */
public class SyncStageDefinitionsPacket {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();
    private static final Type PATH_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type FOLDER_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    private final Map<String, StageEntry> stages;
    private final Map<String, StageEntry> individualStages;
    private final Map<String, String> stagePaths;
    private final Map<String, String> individualStagePaths;
    private final Set<String> folders;
    private final Set<String> individualFolders;

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages) {
        this(stages, StageManager.getIndividualStages());
    }

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages) {
        this(stages, individualStages,
                new HashMap<>(StageManager.getStagePaths()),
                new HashMap<>(StageManager.getIndividualStagePaths()),
                new HashSet<>(StageManager.getFolders()),
                new HashSet<>(StageManager.getIndividualFolders()));
    }

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages,
                                       Map<String, String> stagePaths, Map<String, String> individualStagePaths,
                                       Set<String> folders, Set<String> individualFolders) {
        this.stages = stages;
        this.individualStages = individualStages;
        this.stagePaths = stagePaths;
        this.individualStagePaths = individualStagePaths;
        this.folders = folders;
        this.individualFolders = individualFolders;
    }

    public static void encode(SyncStageDefinitionsPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(GSON.toJson(msg.stages), 262144); // 256KB max
        buffer.writeUtf(GSON.toJson(msg.individualStages), 262144);
        buffer.writeUtf(GSON.toJson(msg.stagePaths), 65536);
        buffer.writeUtf(GSON.toJson(msg.individualStagePaths), 65536);
        buffer.writeUtf(GSON.toJson(msg.folders), 65536);
        buffer.writeUtf(GSON.toJson(msg.individualFolders), 65536);
    }

    public static SyncStageDefinitionsPacket decode(FriendlyByteBuf buffer) {
        Map<String, StageEntry> stages = GSON.fromJson(buffer.readUtf(262144), MAP_TYPE);
        if (stages == null) stages = new HashMap<>();
        Map<String, StageEntry> individualStages = GSON.fromJson(buffer.readUtf(262144), MAP_TYPE);
        if (individualStages == null) individualStages = new HashMap<>();
        Map<String, String> stagePaths = GSON.fromJson(buffer.readUtf(65536), PATH_MAP_TYPE);
        if (stagePaths == null) stagePaths = new HashMap<>();
        Map<String, String> individualStagePaths = GSON.fromJson(buffer.readUtf(65536), PATH_MAP_TYPE);
        if (individualStagePaths == null) individualStagePaths = new HashMap<>();
        Set<String> folders = GSON.fromJson(buffer.readUtf(65536), FOLDER_SET_TYPE);
        if (folders == null) folders = new HashSet<>();
        Set<String> individualFolders = GSON.fromJson(buffer.readUtf(65536), FOLDER_SET_TYPE);
        if (individualFolders == null) individualFolders = new HashSet<>();
        return new SyncStageDefinitionsPacket(stages, individualStages, stagePaths,
                individualStagePaths, folders, individualFolders);
    }

    public static void handle(SyncStageDefinitionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Replace client-side stage definitions with the server's data
            StageManager.setStages(msg.stages);
            StageManager.setIndividualStages(msg.individualStages);
            StageManager.setStagePaths(msg.stagePaths, msg.individualStagePaths);
            StageManager.setFolders(msg.folders, msg.individualFolders);
            StageManager.rebuildDualPhase();
            // Keep editor cache in sync so open editors always show current data
            EditorDataCache.setStages(new HashMap<>(msg.stages));
            System.out.println("[HistoryStages] Received " + msg.stages.size() + " stage definitions + "
                    + msg.individualStages.size() + " individual stage definitions from server.");
        });
        ctx.get().setPacketHandled(true);
    }
}
