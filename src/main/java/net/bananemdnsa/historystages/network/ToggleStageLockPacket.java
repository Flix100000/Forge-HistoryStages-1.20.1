package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.astr0.historystages.api.events.StageEvent;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.network.FriendlyByteBuf;
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

            // Example of how much cleaner the codebase can be if we set up some universal functions
            // which handle unlocking and locking.
            if (msg.unlock) {
                HistoryStages.STAGE_MANAGER.unlockStageForPlayer(player, msg.stageId);
            } else {
                HistoryStages.STAGE_MANAGER.lockStageForPlayer(player, msg.stageId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
