package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Server → Client packet that syncs the server's common config values to the client.
 * Sent on player login and after an admin saves the config via the editor.
 */
public record SyncConfigPacket(Map<String, String> configValues) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncConfigPacket> STREAM_CODEC =
            StreamCodec.of(SyncConfigPacket::encode, SyncConfigPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncConfigPacket msg) {
        buffer.writeInt(msg.configValues.size());
        for (Map.Entry<String, String> entry : msg.configValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static SyncConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SyncConfigPacket(values);
    }

    public static void handle(SyncConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SaveConfigPacket.applyCommonConfig(msg.configValues);
        });
    }

    /**
     * Creates a packet with all current server-side common config values.
     */
    public static SyncConfigPacket fromServerConfig() {
        Map<String, String> values = new HashMap<>();
        values.put("showWelcomeMessage", Config.COMMON.showWelcomeMessage.get().toString());
        values.put("showDebugErrors", Config.COMMON.showDebugErrors.get().toString());
        values.put("enableRuntimeLogging", Config.COMMON.enableRuntimeLogging.get().toString());
        values.put("lockMobLoot", Config.COMMON.lockMobLoot.get().toString());
        values.put("lockBlockBreaking", Config.COMMON.lockBlockBreaking.get().toString());
        values.put("lockedBlockBreakSpeedMultiplier", Config.COMMON.lockedBlockBreakSpeedMultiplier.get().toString());
        values.put("lockItemUsage", Config.COMMON.lockItemUsage.get().toString());
        values.put("lockEntityItems", Config.COMMON.lockEntityItems.get().toString());
        values.put("broadcastChat", Config.COMMON.broadcastChat.get().toString());
        values.put("unlockMessageFormat", Config.COMMON.unlockMessageFormat.get());
        values.put("useActionbar", Config.COMMON.useActionbar.get().toString());
        values.put("useSounds", Config.COMMON.useSounds.get().toString());
        values.put("useToasts", Config.COMMON.useToasts.get().toString());
        values.put("researchTimeInSeconds", Config.COMMON.researchTimeInSeconds.get().toString());
        values.put("useReplacements", Config.COMMON.useReplacements.get().toString());
        values.put("replacementItems", Config.COMMON.replacementItems.get().stream().map(Object::toString).collect(Collectors.joining(",")));
        values.put("replacementTags", Config.COMMON.replacementTags.get().stream().map(Object::toString).collect(Collectors.joining(",")));
        return new SyncConfigPacket(values);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
