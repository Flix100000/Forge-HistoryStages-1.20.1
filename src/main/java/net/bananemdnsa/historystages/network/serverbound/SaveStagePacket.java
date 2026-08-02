package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

public record SaveStagePacket(String stageId, String stageJson, boolean individual, boolean duplicate) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();

    public static final CustomPacketPayload.Type<SaveStagePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_stage"));

    public static final StreamCodec<FriendlyByteBuf, SaveStagePacket> STREAM_CODEC =
            StreamCodec.of(SaveStagePacket::encode, SaveStagePacket::decode);

    public SaveStagePacket(String stageId, StageEntry entry) {
        this(stageId, entry.toJson(), false, false);
    }

    public SaveStagePacket(String stageId, StageEntry entry, boolean individual) {
        this(stageId, entry.toJson(), individual, false);
    }

    public SaveStagePacket(String stageId, StageEntry entry, boolean individual, boolean duplicate) {
        this(stageId, entry.toJson(), individual, duplicate);
    }

    private static void encode(FriendlyByteBuf buffer, SaveStagePacket msg) {
        buffer.writeUtf(msg.stageId);
        buffer.writeUtf(msg.stageJson, 65536);
        buffer.writeBoolean(msg.individual);
        buffer.writeBoolean(msg.duplicate);
    }

    private static SaveStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        String stageJson = buffer.readUtf(65536);
        boolean individual = buffer.readBoolean();
        boolean duplicate = buffer.readBoolean();
        return new SaveStagePacket(stageId, stageJson, individual, duplicate);
    }

    public static void handle(SaveStagePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            StageEntry entry = GSON.fromJson(msg.stageJson, StageEntry.class);
            if (entry == null) return;

            boolean success;
            if (msg.individual) {
                success = StageManager.saveIndividualStage(msg.stageId, entry);
            } else {
                success = StageManager.saveStage(msg.stageId, entry);
            }

            if (success) {
                StageManager.reloadStages();
                StageData data = StageData.get(player.serverLevel());
                // Stage edits can add/remove structure entries — invalidate the
                // structure-lock per-player cache so borders + screen overlay
                // reflect the change on the next server tick.
                net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
                net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
                String titleKey;
                String messageKey;
                if (msg.duplicate) {
                    titleKey = "editor.historystages.toast.stage_duplicated.title";
                    messageKey = "editor.historystages.toast.stage_duplicated.message";
                } else {
                    titleKey = msg.individual
                            ? "editor.historystages.toast.individual_stage_saved.title"
                            : "editor.historystages.toast.stage_saved.title";
                    messageKey = "editor.historystages.toast.stage_saved.message";
                }
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(titleKey, messageKey, msg.stageId),
                        player);
                PacketHandler.reloadRecipesOnly(player.server);
            } else {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.stage_save_failed.title",
                                "editor.historystages.toast.stage_save_failed.message",
                                msg.stageId),
                        player);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
