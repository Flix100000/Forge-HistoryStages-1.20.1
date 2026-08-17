package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class DeleteStagePacket {
    private final String stageId;
    private final boolean individual;

    public DeleteStagePacket(String stageId) {
        this(stageId, false);
    }

    public DeleteStagePacket(String stageId, boolean individual) {
        this.stageId = stageId;
        this.individual = individual;
    }

    public static void encode(DeleteStagePacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.individual);
    }

    public static DeleteStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        boolean individual = buffer.readBoolean();
        return new DeleteStagePacket(stageId, individual);
    }

    public static void handle(DeleteStagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            boolean success;
            if (msg.individual) {
                success = StageManager.deleteIndividualStage(msg.stageId);
            } else {
                success = StageManager.deleteStage(msg.stageId);
            }

            if (success) {
                StageManager.reloadStages();
                StageData data = StageData.get(player.serverLevel());
                // Deleting a stage removes its structure-lock entries — invalidate
                // the per-player cache so borders disappear within one tick.
                net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
                net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
                net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
                String titleKey = msg.individual
                        ? "editor.historystages.toast.individual_stage_deleted.title"
                        : "editor.historystages.toast.stage_deleted.title";
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(titleKey,
                                "editor.historystages.toast.stage_deleted.message", msg.stageId),
                        player);
            } else {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.stage_delete_failed.title",
                                "editor.historystages.toast.stage_delete_failed.message",
                                msg.stageId),
                        player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
