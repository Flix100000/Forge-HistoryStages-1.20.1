package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class SyncIndividualStagesPacket {
    private final Set<String> unlockedStages;

    public SyncIndividualStagesPacket(Set<String> unlockedStages) {
        this.unlockedStages = unlockedStages;
    }

    public static void encode(SyncIndividualStagesPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.unlockedStages.size());
        for (String stage : msg.unlockedStages) {
            buffer.writeUtf(stage);
        }
    }

    public static SyncIndividualStagesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Set<String> stages = new HashSet<>();
        for (int i = 0; i < size; i++) {
            stages.add(buffer.readUtf());
        }
        return new SyncIndividualStagesPacket(stages);
    }

    public static void handle(SyncIndividualStagesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientIndividualStageCache.setUnlockedStages(msg.unlockedStages);

            // No recipe reload needed — individual stages don't affect recipes.
            // Trigger visual refresh for lock icons.
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.levelRenderer != null) {
                        mc.levelRenderer.allChanged();
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
