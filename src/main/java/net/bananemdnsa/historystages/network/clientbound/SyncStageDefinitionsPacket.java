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
import java.util.Map;

/**
 * Syncs all stage definitions (not just unlocked stages) from server to client.
 * Sent on player login so the client knows which items/blocks/entities are locked.
 */
public record SyncStageDefinitionsPacket(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages) {
        this(stages, StageManager.getIndividualStages());
    }

    public static final CustomPacketPayload.Type<SyncStageDefinitionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_stage_definitions"));

    public static final StreamCodec<FriendlyByteBuf, SyncStageDefinitionsPacket> STREAM_CODEC =
            StreamCodec.of(SyncStageDefinitionsPacket::encode, SyncStageDefinitionsPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncStageDefinitionsPacket msg) {
        String json = GSON.toJson(msg.stages);
        buffer.writeUtf(json, 262144);
        String individualJson = GSON.toJson(msg.individualStages);
        buffer.writeUtf(individualJson, 262144);
    }

    private static SyncStageDefinitionsPacket decode(FriendlyByteBuf buffer) {
        String json = buffer.readUtf(262144);
        Map<String, StageEntry> stages = GSON.fromJson(json, MAP_TYPE);
        if (stages == null) stages = new HashMap<>();
        String individualJson = buffer.readUtf(262144);
        Map<String, StageEntry> individualStages = GSON.fromJson(individualJson, MAP_TYPE);
        if (individualStages == null) individualStages = new HashMap<>();
        return new SyncStageDefinitionsPacket(stages, individualStages);
    }

    public static void handle(SyncStageDefinitionsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StageManager.setStages(msg.stages);
            StageManager.setIndividualStages(msg.individualStages);
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
