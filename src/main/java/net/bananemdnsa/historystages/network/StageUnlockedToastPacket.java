package net.bananemdnsa.historystages.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StageUnlockedToastPacket {
    private final String stageName;
    private final String iconId;

    public StageUnlockedToastPacket(String stageName) {
        this(stageName, "");
    }

    public StageUnlockedToastPacket(String stageName, String iconId) {
        this.stageName = stageName;
        this.iconId = iconId != null ? iconId : "";
    }

    public static void encode(StageUnlockedToastPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageName);
        buffer.writeUtf(msg.iconId);
    }

    public static StageUnlockedToastPacket decode(FriendlyByteBuf buffer) {
        String stageName = buffer.readUtf();
        String iconId = buffer.readUtf();
        return new StageUnlockedToastPacket(stageName, iconId);
    }

    public static void handle(StageUnlockedToastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                net.bananemdnsa.historystages.client.ClientToastHandler.showToast(msg.stageName, msg.iconId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
