package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Server → Client packet that syncs the server's common config values to the client.
 * Sent on player login and after an admin saves the config via the editor.
 */
public class SyncConfigPacket {
    private final Map<String, String> configValues;

    public SyncConfigPacket(Map<String, String> configValues) {
        this.configValues = configValues;
    }

    public static void encode(SyncConfigPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.configValues.size());
        for (Map.Entry<String, String> entry : msg.configValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static SyncConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SyncConfigPacket(values);
    }

    public static void handle(SyncConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Apply server's config values on the client
            SaveConfigPacket.applyCommonConfig(msg.configValues);
        });
        ctx.get().setPacketHandled(true);
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
        values.put("replacementTag", Config.COMMON.replacementTag.get().stream().map(Object::toString).collect(Collectors.joining(",")));
        values.put("individualLockItemPickup", Config.COMMON.individualLockItemPickup.get().toString());
        values.put("individualDropOnRevoke", Config.COMMON.individualDropOnRevoke.get().toString());
        values.put("individualNotifyPlayer", Config.COMMON.individualNotifyPlayer.get().toString());
        values.put("individualUnlockMessageFormat", Config.COMMON.individualUnlockMessageFormat.get());
        return new SyncConfigPacket(values);
    }
}
