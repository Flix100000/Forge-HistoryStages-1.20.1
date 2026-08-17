package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.events.lock.BiomeLockHandler;
import net.bananemdnsa.historystages.events.lock.StructureLockHandler;
import net.bananemdnsa.historystages.util.lock.StructureGenerationGate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Deletes a folder inside a stage tree; its content moves up one level. */
public class DeleteFolderPacket {

    private final boolean individual;
    private final String path;

    public DeleteFolderPacket(boolean individual, String path) {
        this.individual = individual;
        this.path = path;
    }

    public static void encode(DeleteFolderPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.individual);
        buffer.writeUtf(msg.path);
    }

    public static DeleteFolderPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        String path = buffer.readUtf();
        return new DeleteFolderPacket(individual, path);
    }

    public static void handle(DeleteFolderPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (!StagePaths.isValid(msg.path) || msg.path.isEmpty()) return;

            if (StageManager.deleteFolder(msg.individual, msg.path)) {
                StageManager.reloadStages();
                // Deleting a folder moves stage files on disk — invalidate the
                // structure-lock per-player cache so borders + screen overlay
                // reflect the change on the next server tick.
                StructureLockHandler.invalidateAll();
                BiomeLockHandler.invalidateAll();
                StructureGenerationGate.rebuild();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.folder_deleted.title",
                                "editor.historystages.toast.folder_deleted.message",
                                StagePaths.name(msg.path)),
                        player);
            } else {
                // A failed delete is not a no-op: the children are moved one at a time, so a
                // failure part-way through leaves the ones before it in their new place. Reload
                // and re-sync exactly like the success branch, otherwise the server and every
                // client keep describing the old layout and the next save writes to a path that
                // no longer exists.
                StageManager.reloadStages();
                StructureLockHandler.invalidateAll();
                BiomeLockHandler.invalidateAll();
                StructureGenerationGate.rebuild();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.folder_failed.title",
                                "editor.historystages.toast.folder_failed.message",
                                StagePaths.name(msg.path)),
                        player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
