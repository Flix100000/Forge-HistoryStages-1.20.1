package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record LockFeedbackPacket(byte kind, List<String> displayNames) implements CustomPacketPayload {

    public static final byte KIND_DIMENSION = 0;
    public static final byte KIND_MOB = 1;

    public static final CustomPacketPayload.Type<LockFeedbackPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "lock_feedback"));

    public static final StreamCodec<FriendlyByteBuf, LockFeedbackPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeByte(msg.kind);
                        buf.writeVarInt(msg.displayNames.size());
                        for (String name : msg.displayNames) {
                            buf.writeUtf(name);
                        }
                    },
                    buf -> {
                        byte kind = buf.readByte();
                        int size = buf.readVarInt();
                        List<String> names = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            names.add(buf.readUtf());
                        }
                        return new LockFeedbackPacket(kind, names);
                    }
            );

    public static void handle(LockFeedbackPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            boolean isDimension = msg.kind == KIND_DIMENSION;
            boolean showChat = isDimension
                    ? Config.VISUAL.dimShowChat.get()
                    : Config.VISUAL.mobShowChat.get();
            boolean showStagesInChat = isDimension
                    ? Config.VISUAL.dimShowStagesInChat.get()
                    : Config.VISUAL.mobShowStagesInChat.get();
            boolean useActionbar = isDimension
                    ? Config.VISUAL.dimUseActionbar.get()
                    : Config.VISUAL.mobUseActionbar.get();

            String chatKey = isDimension
                    ? "message.historystages.dimension_locked"
                    : "message.historystages.mob_locked";

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
                MutableComponent actionbarMsg = isDimension
                        ? net.bananemdnsa.historystages.util.lock.LockMessages.dimensionUnknown()
                        : net.bananemdnsa.historystages.util.lock.LockMessages.mobUnknown();
                mc.player.displayClientMessage(
                        actionbarMsg.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                        true
                );
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
