package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

public record SyncIndividualStagesPacket(Set<String> unlockedStages) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncIndividualStagesPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_individual_stages"));

    public static final StreamCodec<FriendlyByteBuf, SyncIndividualStagesPacket> STREAM_CODEC =
            StreamCodec.of(SyncIndividualStagesPacket::encode, SyncIndividualStagesPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncIndividualStagesPacket msg) {
        buffer.writeInt(msg.unlockedStages.size());
        for (String stage : msg.unlockedStages) {
            buffer.writeUtf(stage);
        }
    }

    private static SyncIndividualStagesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Set<String> stages = new HashSet<>();
        for (int i = 0; i < size; i++) {
            stages.add(buffer.readUtf());
        }
        return new SyncIndividualStagesPacket(stages);
    }

    public static void handle(SyncIndividualStagesPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientIndividualStageCache.setUnlockedStages(msg.unlockedStages);

            // Trigger visual refresh for lock icons
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.levelRenderer != null) {
                mc.levelRenderer.allChanged();
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
