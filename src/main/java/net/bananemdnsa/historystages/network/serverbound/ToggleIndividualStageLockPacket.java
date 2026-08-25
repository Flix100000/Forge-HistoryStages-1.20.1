package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Client → Server: editor toggle for an individual stage. An empty {@code target}
 * means "@a" — every online player. Mirrors {@link ToggleStageLockPacket}, but routes
 * through {@link StageStates} so affected players get the same sync packet,
 * events, notifications and (on lock) inventory cleanup the /stage command produces.
 *
 * <p>Like the global editor toggle, this deliberately bypasses dependency checks —
 * the editor is an admin override.</p>
 */
public record ToggleIndividualStageLockPacket(String stageId, Optional<UUID> target, boolean unlock)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleIndividualStageLockPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "toggle_individual_stage_lock"));

    public static final StreamCodec<FriendlyByteBuf, ToggleIndividualStageLockPacket> STREAM_CODEC =
            StreamCodec.of(ToggleIndividualStageLockPacket::encode, ToggleIndividualStageLockPacket::decode);

    private static void encode(FriendlyByteBuf buffer, ToggleIndividualStageLockPacket msg) {
        buffer.writeUtf(msg.stageId);
        buffer.writeOptional(msg.target, (buf, uuid) -> buf.writeUUID(uuid));
        buffer.writeBoolean(msg.unlock);
    }

    private static ToggleIndividualStageLockPacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        Optional<UUID> target = buffer.readOptional(buf -> buf.readUUID());
        boolean unlock = buffer.readBoolean();
        return new ToggleIndividualStageLockPacket(stageId, target, unlock);
    }

    public static void handle(ToggleIndividualStageLockPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) return;
            if (!sender.hasPermissions(2)) return;

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
                        ? StageStates.unlockIndividual(msg.stageId, target)
                        : StageStates.relockIndividual(msg.stageId, target);
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
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
