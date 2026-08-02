package net.bananemdnsa.historystages.network.clientbound;
import net.bananemdnsa.historystages.network.EditorDataCache;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Syncs all stage definitions (not just unlocked stages) from server to client.
 * Sent on player login so the client knows which items/blocks/entities are locked.
 *
 * <p>Also carries the folder layout of both trees (stage id → folder path, and every
 * folder including empty ones), because the client never sees the config files on a
 * dedicated server — the folder tree only reaches it through this sync.
 */
public record SyncStageDefinitionsPacket(Map<String, StageEntry> stages,
                                         Map<String, StageEntry> individualStages,
                                         Map<String, String> stagePaths,
                                         Map<String, String> individualStagePaths,
                                         Set<String> folders,
                                         Set<String> individualFolders) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();
    private static final java.lang.reflect.Type PATH_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final java.lang.reflect.Type FOLDER_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages) {
        this(stages, StageManager.getIndividualStages(),
                new HashMap<>(StageManager.getStagePaths()),
                new HashMap<>(StageManager.getIndividualStagePaths()),
                new HashSet<>(StageManager.getFolders()),
                new HashSet<>(StageManager.getIndividualFolders()));
    }

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages) {
        this(stages, individualStages,
                new HashMap<>(StageManager.getStagePaths()),
                new HashMap<>(StageManager.getIndividualStagePaths()),
                new HashSet<>(StageManager.getFolders()),
                new HashSet<>(StageManager.getIndividualFolders()));
    }

    public static final CustomPacketPayload.Type<SyncStageDefinitionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_stage_definitions"));

    public static final StreamCodec<FriendlyByteBuf, SyncStageDefinitionsPacket> STREAM_CODEC =
            StreamCodec.of(SyncStageDefinitionsPacket::encode, SyncStageDefinitionsPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncStageDefinitionsPacket msg) {
        buffer.writeUtf(GSON.toJson(msg.stages), 262144);
        buffer.writeUtf(GSON.toJson(msg.individualStages), 262144);
        buffer.writeUtf(GSON.toJson(msg.stagePaths), 65536);
        buffer.writeUtf(GSON.toJson(msg.individualStagePaths), 65536);
        buffer.writeUtf(GSON.toJson(msg.folders), 65536);
        buffer.writeUtf(GSON.toJson(msg.individualFolders), 65536);
    }

    private static SyncStageDefinitionsPacket decode(FriendlyByteBuf buffer) {
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

    public static void handle(SyncStageDefinitionsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StageManager.setStages(msg.stages);
            StageManager.setIndividualStages(msg.individualStages);
            StageManager.setStagePaths(msg.stagePaths, msg.individualStagePaths);
            StageManager.setFolders(msg.folders, msg.individualFolders);
            StageManager.rebuildDualPhase();
            EditorDataCache.setStages(new HashMap<>(msg.stages));
            System.out.println("[HistoryStages] Received " + msg.stages.size() + " stage definitions + "
                    + msg.individualStages.size() + " individual stage definitions from server.");

            // Stage definitions changed at runtime — invalidate the creative tab cache so
            // newly-non-AUTO stages get their scroll, and former non-AUTO stages lose theirs.
            // Mirror vanilla CreativeModeInventoryScreen.tryRebuildTabContents: after the
            // rebuild, re-register the creative search reloaders with the fresh item list,
            // otherwise the search field keeps the stale (or empty) captured list.
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                net.minecraft.core.HolderLookup.Provider registryAccess = mc.level.registryAccess();
                boolean rebuilt = net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
                        mc.player.connection.enabledFeatures(),
                        mc.options.operatorItemsTab().get() && mc.player.canUseGameMasterBlocks(),
                        registryAccess);
                if (rebuilt) {
                    java.util.List<net.minecraft.world.item.ItemStack> searchItems = java.util.List.copyOf(
                            net.minecraft.world.item.CreativeModeTabs.searchTab().getDisplayItems());
                    net.minecraft.client.multiplayer.SessionSearchTrees searchTrees =
                            mc.player.connection.searchTrees();
                    searchTrees.updateCreativeTooltips(registryAccess, searchItems);
                    searchTrees.updateCreativeTags(searchItems);
                }
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
