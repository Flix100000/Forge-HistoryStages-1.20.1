package net.bananemdnsa.historystages.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StageUnlockedToastPacket {
    private final String stageName;

    public StageUnlockedToastPacket(String stageName) {
        this.stageName = stageName;
    }

    public static void encode(StageUnlockedToastPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageName);
    }

    public static StageUnlockedToastPacket decode(FriendlyByteBuf buffer) {
        return new StageUnlockedToastPacket(buffer.readUtf());
    }

    public static void handle(StageUnlockedToastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                net.bananemdnsa.historystages.client.ClientToastHandler.showToast(msg.stageName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
