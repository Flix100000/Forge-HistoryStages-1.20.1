package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageUnlockHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record ToggleStageLockPacket(String stageId, boolean unlock) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleStageLockPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "toggle_stage_lock"));

    public static final StreamCodec<FriendlyByteBuf, ToggleStageLockPacket> STREAM_CODEC =
            StreamCodec.of(ToggleStageLockPacket::encode, ToggleStageLockPacket::decode);

    private static void encode(FriendlyByteBuf buffer, ToggleStageLockPacket msg) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.unlock);
    }

    private static ToggleStageLockPacket decode(FriendlyByteBuf buffer) {
        return new ToggleStageLockPacket(buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(ToggleStageLockPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            if (!StageManager.getStages().containsKey(msg.stageId)) return;

            var entry = StageManager.getStages().get(msg.stageId);
            String displayName = entry != null ? entry.getDisplayName() : msg.stageId;

            // Through the helper, not rebuilt here. This handler used to do its own version of
            // unlockGlobal and the two drifted in both directions: the editor never played the
            // unlock sound or sent the toast, and every other caller — pedestal, command,
            // auto-trigger, quest reward — never cleared the structure and biome caches or
            // reloaded recipes. Everything either side had is now in the helper.
            if (msg.unlock) {
                StageUnlockHelper.unlockGlobal(msg.stageId, player.serverLevel());
            } else {
                StageUnlockHelper.relockGlobal(msg.stageId, player.serverLevel());
            }

            String titleKey = msg.unlock
                    ? "editor.historystages.toast.stage_unlocked_editor.title"
                    : "editor.historystages.toast.stage_locked_editor.title";
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(titleKey,
                            "editor.historystages.toast.stage_lock_changed.message",
                            displayName),
                    player);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
