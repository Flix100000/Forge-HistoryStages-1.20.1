package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageJsonLimits;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class SaveStagePacket {
    private static final Gson GSON = new Gson();

    private final String stageId;
    private final String stageJson;
    private final boolean individual;
    private final boolean duplicate;

    public SaveStagePacket(String stageId, StageEntry entry) {
        this(stageId, entry, false, false);
    }

    public SaveStagePacket(String stageId, StageEntry entry, boolean individual) {
        this(stageId, entry, individual, false);
    }

    public SaveStagePacket(String stageId, StageEntry entry, boolean individual, boolean duplicate) {
        this(stageId, entry.toCompactJson(), individual, duplicate);
    }

    /** {@code stageJson} must already be the compact form — see {@link StageEntry#toCompactJson()}. */
    public SaveStagePacket(String stageId, String stageJson, boolean individual, boolean duplicate) {
        this.stageId = stageId;
        this.stageJson = stageJson;
        this.individual = individual;
        this.duplicate = duplicate;
    }

    public static void encode(SaveStagePacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeUtf(msg.stageJson, StageJsonLimits.MAX_STAGE_JSON);
        buffer.writeBoolean(msg.individual);
        buffer.writeBoolean(msg.duplicate);
    }

    public static SaveStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        String stageJson = buffer.readUtf(StageJsonLimits.MAX_STAGE_JSON);
        boolean individual = buffer.readBoolean();
        boolean duplicate = buffer.readBoolean();
        return new SaveStagePacket(stageId, stageJson, individual, duplicate);
    }

    public static void handle(SaveStagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

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
            } else {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.stage_save_failed.title",
                                "editor.historystages.toast.stage_save_failed.message",
                                msg.stageId),
                        player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
