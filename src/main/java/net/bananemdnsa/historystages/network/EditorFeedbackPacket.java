package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.EditorToast;
import net.bananemdnsa.historystages.client.editor.EditorToastHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client notification for editor actions. Renders as an
 * editor-styled toast (see {@link EditorToast}) instead of a chat message.
 *
 * <p>Title and body are sent as translation keys with string arguments so
 * future call sites can reuse the system without changing the payload shape.
 */
public record EditorFeedbackPacket(byte level, String titleKey, String messageKey, List<String> args)
        implements CustomPacketPayload {

    public static final byte LEVEL_SUCCESS = 0;
    public static final byte LEVEL_ERROR = 1;
    public static final byte LEVEL_INFO = 2;

    public static final CustomPacketPayload.Type<EditorFeedbackPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "editor_feedback"));

    public static final StreamCodec<FriendlyByteBuf, EditorFeedbackPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeByte(msg.level);
                        buf.writeUtf(msg.titleKey);
                        buf.writeUtf(msg.messageKey);
                        buf.writeVarInt(msg.args.size());
                        for (String a : msg.args) buf.writeUtf(a);
                    },
                    buf -> {
                        byte level = buf.readByte();
                        String titleKey = buf.readUtf();
                        String messageKey = buf.readUtf();
                        int size = buf.readVarInt();
                        List<String> args = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) args.add(buf.readUtf());
                        return new EditorFeedbackPacket(level, titleKey, messageKey, args);
                    }
            );

    public static EditorFeedbackPacket success(String titleKey, String messageKey, String... args) {
        return new EditorFeedbackPacket(LEVEL_SUCCESS, titleKey, messageKey, List.of(args));
    }

    public static EditorFeedbackPacket error(String titleKey, String messageKey, String... args) {
        return new EditorFeedbackPacket(LEVEL_ERROR, titleKey, messageKey, List.of(args));
    }

    public static EditorFeedbackPacket info(String titleKey, String messageKey, String... args) {
        return new EditorFeedbackPacket(LEVEL_INFO, titleKey, messageKey, List.of(args));
    }

    public static void handle(EditorFeedbackPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            EditorToast.Level lvl = switch (msg.level) {
                case LEVEL_ERROR -> EditorToast.Level.ERROR;
                case LEVEL_INFO -> EditorToast.Level.INFO;
                default -> EditorToast.Level.SUCCESS;
            };
            Component title = Component.translatable(msg.titleKey);
            Object[] argArr = msg.args.toArray();
            Component message = Component.translatable(msg.messageKey, argArr);
            EditorToastHandler.show(lvl, title, message);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
