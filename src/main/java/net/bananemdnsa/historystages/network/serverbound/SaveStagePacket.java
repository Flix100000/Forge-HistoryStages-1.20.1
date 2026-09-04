package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageFileGuard;
import net.bananemdnsa.historystages.data.StageJsonLimits;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

public record SaveStagePacket(String stageId, String stageJson, boolean individual,
                              boolean duplicate, String folder) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();

    public static final CustomPacketPayload.Type<SaveStagePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_stage"));

    public static final StreamCodec<FriendlyByteBuf, SaveStagePacket> STREAM_CODEC =
            StreamCodec.of(SaveStagePacket::encode, SaveStagePacket::decode);

    public SaveStagePacket(String stageId, StageEntry entry) {
        this(stageId, entry.toCompactJson(), false, false, "");
    }

    public SaveStagePacket(String stageId, StageEntry entry, boolean individual) {
        this(stageId, entry.toCompactJson(), individual, false, "");
    }

    public SaveStagePacket(String stageId, StageEntry entry, boolean individual, boolean duplicate) {
        this(stageId, entry.toCompactJson(), individual, duplicate, "");
    }

    /** {@code folder} is the folder the editor was standing in; it only applies to a new stage. */
    public SaveStagePacket(String stageId, StageEntry entry, boolean individual, boolean duplicate, String folder) {
        this(stageId, entry.toCompactJson(), individual, duplicate, folder == null ? "" : folder);
    }

    private static void encode(FriendlyByteBuf buffer, SaveStagePacket msg) {
        buffer.writeUtf(msg.stageId);
        buffer.writeUtf(msg.stageJson, StageJsonLimits.MAX_STAGE_JSON);
        buffer.writeBoolean(msg.individual);
        buffer.writeBoolean(msg.duplicate);
        buffer.writeUtf(msg.folder);
    }

    private static SaveStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        String stageJson = buffer.readUtf(StageJsonLimits.MAX_STAGE_JSON);
        boolean individual = buffer.readBoolean();
        boolean duplicate = buffer.readBoolean();
        String folder = buffer.readUtf();
        return new SaveStagePacket(stageId, stageJson, individual, duplicate, folder);
    }

    public static void handle(SaveStagePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            StageEntry entry = GSON.fromJson(msg.stageJson, StageEntry.class);
            if (entry == null) return;

            if (!net.bananemdnsa.historystages.data.StagePaths.isValid(msg.folder)) {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.stage_save_failed.title",
                                "editor.historystages.toast.stage_save_failed.message",
                                msg.stageId),
                        player);
                return;
            }

            // Guards against silently clobbering a hand edit made to the file while the editor
            // held a stale in-memory copy: refuse the write unless the file on disk still
            // matches what the server last loaded, or the player already confirmed this exact
            // on-disk state once.
            StageScope scope = msg.individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
            byte[] onDisk = StageManager.stageFileBytes(msg.stageId, msg.individual, msg.folder);
            if (!StageFileGuard.mayWrite(player.getUUID(), msg.stageId, scope, onDisk)) {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.stage_changed_on_disk.title",
                                "editor.historystages.toast.stage_changed_on_disk.message",
                                msg.stageId),
                        player);
                return;
            }

            boolean success;
            if (msg.individual) {
                success = StageManager.saveIndividualStage(msg.stageId, entry, msg.folder);
            } else {
                success = StageManager.saveStage(msg.stageId, entry, msg.folder);
            }

            if (success) {
                StageFileGuard.consume(player.getUUID(), msg.stageId, scope);
                StageManager.reloadStages();
                StageData data = StageData.get(player.serverLevel());
                // Stage edits can add/remove structure entries — invalidate the
                // structure-lock per-player cache so borders + screen overlay
                // reflect the change on the next server tick.
                net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
                net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
                net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
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
                PacketHandler.reloadForLockChange(player.server);
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
