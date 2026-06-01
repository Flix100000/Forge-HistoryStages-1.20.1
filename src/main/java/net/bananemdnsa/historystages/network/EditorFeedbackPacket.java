package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.client.editor.EditorToast;
import net.bananemdnsa.historystages.client.editor.EditorToastHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client notification for editor actions. Renders as an
 * editor-styled toast (see EditorToast) instead of a chat message.
 *
 * Title and body are sent as translation keys with string arguments so
 * future call sites can reuse the system without changing the payload shape.
 */
public class EditorFeedbackPacket {

    public static final byte LEVEL_SUCCESS = 0;
    public static final byte LEVEL_ERROR = 1;
    public static final byte LEVEL_INFO = 2;

    private final byte level;
    private final String titleKey;
    private final String messageKey;
    private final List<String> args;

    public EditorFeedbackPacket(byte level, String titleKey, String messageKey, List<String> args) {
        this.level = level;
        this.titleKey = titleKey;
        this.messageKey = messageKey;
        this.args = args;
    }

    public static EditorFeedbackPacket success(String titleKey, String messageKey, String... args) {
        return new EditorFeedbackPacket(LEVEL_SUCCESS, titleKey, messageKey, List.of(args));
    }

    public static EditorFeedbackPacket error(String titleKey, String messageKey, String... args) {
        return new EditorFeedbackPacket(LEVEL_ERROR, titleKey, messageKey, List.of(args));
    }

    public static EditorFeedbackPacket info(String titleKey, String messageKey, String... args) {
        return new EditorFeedbackPacket(LEVEL_INFO, titleKey, messageKey, List.of(args));
    }

    public static void encode(EditorFeedbackPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.level);
        buf.writeUtf(msg.titleKey);
        buf.writeUtf(msg.messageKey);
        buf.writeVarInt(msg.args.size());
        for (String a : msg.args) buf.writeUtf(a);
    }

    public static EditorFeedbackPacket decode(FriendlyByteBuf buf) {
        byte level = buf.readByte();
        String titleKey = buf.readUtf();
        String messageKey = buf.readUtf();
        int size = buf.readVarInt();
        List<String> args = new ArrayList<>(size);
        for (int i = 0; i < size; i++) args.add(buf.readUtf());
        return new EditorFeedbackPacket(level, titleKey, messageKey, args);
    }

    public static void handle(EditorFeedbackPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
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
        ctx.get().setPacketHandled(true);
    }
}
