package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageUnlockHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server: editor toggle for an individual stage. An empty {@code target}
 * means "@a" — every online player. Mirrors {@link ToggleStageLockPacket}, but routes
 * through {@link StageUnlockHelper} so affected players get the same sync packet,
 * events, notifications and (on lock) inventory cleanup the /stage command produces.
 *
 * <p>Like the global editor toggle, this deliberately bypasses dependency checks —
 * the editor is an admin override.</p>
 */
public class ToggleIndividualStageLockPacket {

    private final String stageId;
    private final Optional<UUID> target;
    private final boolean unlock;

    public ToggleIndividualStageLockPacket(String stageId, Optional<UUID> target, boolean unlock) {
        this.stageId = stageId;
        this.target = target;
        this.unlock = unlock;
    }

    public static void encode(ToggleIndividualStageLockPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeOptional(msg.target, (buf, uuid) -> buf.writeUUID(uuid));
        buffer.writeBoolean(msg.unlock);
    }

    public static ToggleIndividualStageLockPacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        Optional<UUID> target = buffer.readOptional(buf -> buf.readUUID());
        boolean unlock = buffer.readBoolean();
        return new ToggleIndividualStageLockPacket(stageId, target, unlock);
    }

    public static void handle(ToggleIndividualStageLockPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            StageEntry entry = StageManager.getIndividualStages().get(msg.stageId);
            if (entry == null) return;
            String displayName = entry.getDisplayName();

            List<ServerPlayer> targets = new ArrayList<>();
            if (msg.target.isPresent()) {
                ServerPlayer target = sender.server.getPlayerList().getPlayer(msg.target.get());
                if (target != null) targets.add(target);
            } else {
                targets.addAll(sender.server.getPlayerList().getPlayers());
            }
            if (targets.isEmpty()) return;

            int changed = 0;
            for (ServerPlayer target : targets) {
                boolean applied = msg.unlock
                        ? StageUnlockHelper.unlockIndividual(msg.stageId, target)
                        : StageUnlockHelper.relockIndividual(msg.stageId, target);
                if (applied) changed++;
            }

            String titleKey = msg.unlock
                    ? "editor.historystages.toast.individual_unlocked_editor.title"
                    : "editor.historystages.toast.individual_locked_editor.title";
            EditorFeedbackPacket feedback = msg.target.isPresent()
                    ? EditorFeedbackPacket.successForPlayer(titleKey,
                            "editor.historystages.toast.individual_lock_changed.message",
                            targets.get(0).getUUID(),
                            displayName, targets.get(0).getName().getString())
                    : EditorFeedbackPacket.success(titleKey,
                            "editor.historystages.toast.individual_lock_changed_all.message",
                            displayName, String.valueOf(changed));
            PacketHandler.sendEditorFeedback(feedback, sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
