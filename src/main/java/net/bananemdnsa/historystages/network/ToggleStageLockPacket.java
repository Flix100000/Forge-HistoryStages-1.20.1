package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.events.StageEvent;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

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

            StageData data = StageData.get(player.serverLevel());
            var entry = StageManager.getStages().get(msg.stageId);
            String displayName = entry != null ? entry.getDisplayName() : msg.stageId;

            if (msg.unlock) {
                data.addStage(msg.stageId);
                NeoForge.EVENT_BUS.post(new StageEvent.Unlocked(msg.stageId, displayName));
            } else {
                data.removeStage(msg.stageId);
                NeoForge.EVENT_BUS.post(new StageEvent.Locked(msg.stageId, displayName));
            }

            data.setDirty();
            StageData.SERVER_CACHE.clear();
            StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
            PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
            PacketHandler.reloadRecipesOnly(player.server);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
