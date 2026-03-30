package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

public record SaveStagePacket(String stageId, String stageJson) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();

    public static final CustomPacketPayload.Type<SaveStagePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_stage"));

    public static final StreamCodec<FriendlyByteBuf, SaveStagePacket> STREAM_CODEC =
            StreamCodec.of(SaveStagePacket::encode, SaveStagePacket::decode);

    public SaveStagePacket(String stageId, StageEntry entry) {
        this(stageId, entry.toJson());
    }

    private static void encode(FriendlyByteBuf buffer, SaveStagePacket msg) {
        buffer.writeUtf(msg.stageId);
        buffer.writeUtf(msg.stageJson, 65536);
    }

    private static SaveStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        String stageJson = buffer.readUtf(65536);
        return new SaveStagePacket(stageId, stageJson);
    }

    public static void handle(SaveStagePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            StageEntry entry = GSON.fromJson(msg.stageJson, StageEntry.class);
            if (entry == null) return;

            boolean success = StageManager.saveStage(msg.stageId, entry);
            if (success) {
                StageManager.reloadStages();
                StageData data = StageData.get(player.serverLevel());
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §aStage '" + msg.stageId + "' saved successfully."));
                PacketHandler.reloadRecipesOnly(player.server);
            } else {
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §cFailed to save stage '" + msg.stageId + "'."));
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
