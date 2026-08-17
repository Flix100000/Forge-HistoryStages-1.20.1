package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Renames a folder in place. Stage IDs are file names and stay untouched. */
public class RenameFolderPacket {

    private final boolean individual;
    private final String path;
    private final String newName;

    public RenameFolderPacket(boolean individual, String path, String newName) {
        this.individual = individual;
        this.path = path;
        this.newName = newName;
    }

    public static void encode(RenameFolderPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.individual);
        buffer.writeUtf(msg.path);
        buffer.writeUtf(msg.newName);
    }

    public static RenameFolderPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        String path = buffer.readUtf();
        String newName = buffer.readUtf();
        return new RenameFolderPacket(individual, path, newName);
    }

    public static void handle(RenameFolderPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (!StagePaths.isValid(msg.path) || msg.path.isEmpty()) return;
            if (!StagePaths.isValidSegment(msg.newName)) return;

            if (StageManager.renameFolder(msg.individual, msg.path, msg.newName)) {
                StageManager.reloadStages();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.folder_renamed.title",
                                "editor.historystages.toast.folder_renamed.message",
                                msg.newName),
                        player);
            } else {
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
