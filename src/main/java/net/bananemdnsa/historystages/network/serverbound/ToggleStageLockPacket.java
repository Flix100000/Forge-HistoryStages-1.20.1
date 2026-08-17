package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.events.StageEvent;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class ToggleStageLockPacket {
    private final String stageId;
    private final boolean unlock; // true = unlock, false = lock

    public ToggleStageLockPacket(String stageId, boolean unlock) {
        this.stageId = stageId;
        this.unlock = unlock;
    }

    public static void encode(ToggleStageLockPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.unlock);
    }

    public static ToggleStageLockPacket decode(FriendlyByteBuf buffer) {
        return new ToggleStageLockPacket(buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(ToggleStageLockPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            if (!StageManager.getStages().containsKey(msg.stageId)) return;

            StageData data = StageData.get(player.serverLevel());
            var entry = StageManager.getStages().get(msg.stageId);
            String displayName = entry != null ? entry.getDisplayName() : msg.stageId;

            if (msg.unlock) {
                data.addStage(msg.stageId);
                MinecraftForge.EVENT_BUS.post(new StageEvent.Unlocked(msg.stageId, displayName));
            } else {
                data.removeStage(msg.stageId);
                MinecraftForge.EVENT_BUS.post(new StageEvent.Locked(msg.stageId, displayName));
            }

            data.setDirty();
            StageData.refreshCache(data.getUnlockedStages());
            // Structure-lock caches are keyed off lockedStructureIds — force every
            // tracked player to recompute on the next tick so the force-field /
            // screen overlay appear immediately when a stage gets locked again.
            net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
            PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

            String titleKey = msg.unlock
                    ? "editor.historystages.toast.stage_unlocked_editor.title"
                    : "editor.historystages.toast.stage_locked_editor.title";
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(titleKey,
                            "editor.historystages.toast.stage_lock_changed.message",
                            displayName),
                    player);
        });
        ctx.get().setPacketHandled(true);
    }
}
