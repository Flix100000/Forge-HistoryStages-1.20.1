package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LockFeedbackPacket {

    public static final byte KIND_DIMENSION = 0;
    public static final byte KIND_MOB = 1;

    private final byte kind;
    private final List<String> displayNames;

    public LockFeedbackPacket(byte kind, List<String> displayNames) {
        this.kind = kind;
        this.displayNames = displayNames;
    }

    public static void encode(LockFeedbackPacket msg, FriendlyByteBuf buffer) {
        buffer.writeByte(msg.kind);
        buffer.writeVarInt(msg.displayNames.size());
        for (String name : msg.displayNames) {
            buffer.writeUtf(name);
        }
    }

    public static LockFeedbackPacket decode(FriendlyByteBuf buffer) {
        byte kind = buffer.readByte();
        int size = buffer.readVarInt();
        List<String> names = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            names.add(buffer.readUtf());
        }
        return new LockFeedbackPacket(kind, names);
    }

    public static void handle(LockFeedbackPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientHandler.display(msg);
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientHandler {
        static void display(LockFeedbackPacket msg) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;

            boolean isDimension = msg.kind == KIND_DIMENSION;
            boolean showChat = isDimension
                    ? Config.CLIENT.dimShowChat.get()
                    : Config.CLIENT.mobShowChat.get();
            boolean showStagesInChat = isDimension
                    ? Config.CLIENT.dimShowStagesInChat.get()
                    : Config.CLIENT.mobShowStagesInChat.get();
            boolean useActionbar = isDimension
                    ? Config.CLIENT.dimUseActionbar.get()
                    : Config.CLIENT.mobUseActionbar.get();

            String chatKey = isDimension
                    ? "message.historystages.dimension_locked"
                    : "message.historystages.mob_locked";
            String actionbarKey = isDimension
                    ? "message.historystages.dimension_unknown"
                    : "message.historystages.mob_unknown";

            if (showChat) {
                MutableComponent chatMsg = Component.translatable(chatKey);
                if (showStagesInChat) {
                    for (String displayName : msg.displayNames) {
                        chatMsg.append(Component.translatable("message.historystages.locked_stage", displayName));
                    }
                }
                mc.player.sendSystemMessage(chatMsg);
            }

            if (useActionbar) {
                mc.player.displayClientMessage(
                        Component.translatable(actionbarKey)
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                        true
                );
            }
        }
    }
}
