package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Creates a folder inside a stage tree. {@code path} is relative to the tree root. */
public class CreateFolderPacket {

    private final boolean individual;
    private final String path;

    public CreateFolderPacket(boolean individual, String path) {
        this.individual = individual;
        this.path = path;
    }

    public static void encode(CreateFolderPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.individual);
        buffer.writeUtf(msg.path);
    }

    public static CreateFolderPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        String path = buffer.readUtf();
        return new CreateFolderPacket(individual, path);
    }

    public static void handle(CreateFolderPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (!StagePaths.isValid(msg.path) || msg.path.isEmpty()) return;

            if (StageManager.createFolder(msg.individual, msg.path)) {
                StageManager.reloadStages();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.folder_created.title",
                                "editor.historystages.toast.folder_created.message",
                                StagePaths.name(msg.path)),
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
