package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SaveConfigPacket {
    private final Map<String, String> configValues;
    private final boolean isClient; // true = client config, false = common config

    public SaveConfigPacket(Map<String, String> configValues, boolean isClient) {
        this.configValues = configValues;
        this.isClient = isClient;
    }

    public static void encode(SaveConfigPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.isClient);
        buffer.writeInt(msg.configValues.size());
        for (Map.Entry<String, String> entry : msg.configValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    public static SaveConfigPacket decode(FriendlyByteBuf buffer) {
        boolean isClient = buffer.readBoolean();
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SaveConfigPacket(values, isClient);
    }

    public static void handle(SaveConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            // Apply common config values on the server
            if (!msg.isClient) {
                applyCommonConfig(msg.configValues);
                // Force Forge to persist the TOML file to disk
                Config.COMMON_SPEC.save();
                // Sync updated config to all connected clients
                PacketHandler.sendConfigToAll(SyncConfigPacket.fromServerConfig());
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §aCommon config saved."));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void applyCommonConfig(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            switch (key) {
                case "showWelcomeMessage" -> Config.COMMON.showWelcomeMessage.set(Boolean.parseBoolean(value));
                case "showDebugErrors" -> Config.COMMON.showDebugErrors.set(Boolean.parseBoolean(value));
                case "enableRuntimeLogging" -> Config.COMMON.enableRuntimeLogging.set(Boolean.parseBoolean(value));
                case "lockMobLoot" -> Config.COMMON.lockMobLoot.set(Boolean.parseBoolean(value));
                case "lockBlockBreaking" -> Config.COMMON.lockBlockBreaking.set(Boolean.parseBoolean(value));
                case "lockedBlockBreakSpeedMultiplier" -> {
                    try { Config.COMMON.lockedBlockBreakSpeedMultiplier.set(Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
                }
                case "lockItemUsage" -> Config.COMMON.lockItemUsage.set(Boolean.parseBoolean(value));
                case "lockEntityItems" -> Config.COMMON.lockEntityItems.set(Boolean.parseBoolean(value));
                case "lockBlockInteraction" -> Config.COMMON.lockBlockInteraction.set(Boolean.parseBoolean(value));
                case "broadcastChat" -> Config.COMMON.broadcastChat.set(Boolean.parseBoolean(value));
                case "unlockMessageFormat" -> Config.COMMON.unlockMessageFormat.set(value);
                case "useActionbar" -> Config.COMMON.useActionbar.set(Boolean.parseBoolean(value));
                case "useSounds" -> Config.COMMON.useSounds.set(Boolean.parseBoolean(value));
                case "useToasts" -> Config.COMMON.useToasts.set(Boolean.parseBoolean(value));
                case "researchTimeInSeconds" -> {
                    try { Config.COMMON.researchTimeInSeconds.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                }
                case "useReplacements" -> Config.COMMON.useReplacements.set(Boolean.parseBoolean(value));
                case "replacementItems" -> {
                    List<String> itemList = Arrays.stream(value.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    Config.COMMON.replacementItems.set(itemList);
                }
                case "replacementTag" -> {
                    List<String> tagList = Arrays.stream(value.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    Config.COMMON.replacementTag.set(tagList);
                }
                case "individualLockItemPickup" -> Config.COMMON.individualLockItemPickup.set(Boolean.parseBoolean(value));
                case "individualDropOnRevoke" -> Config.COMMON.individualDropOnRevoke.set(Boolean.parseBoolean(value));
                case "individualLockBlockBreaking" -> Config.COMMON.individualLockBlockBreaking.set(Boolean.parseBoolean(value));
                case "individualLockedBlockBreakSpeedMultiplier" -> {
                    try { Config.COMMON.individualLockedBlockBreakSpeedMultiplier.set(Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
                }
                case "individualLockItemUsage" -> Config.COMMON.individualLockItemUsage.set(Boolean.parseBoolean(value));
                case "individualLockBlockInteraction" -> Config.COMMON.individualLockBlockInteraction.set(Boolean.parseBoolean(value));
                case "individualBroadcastChat" -> Config.COMMON.individualBroadcastChat.set(Boolean.parseBoolean(value));
                case "individualUnlockMessageFormat" -> Config.COMMON.individualUnlockMessageFormat.set(value);
                case "individualUseActionbar" -> Config.COMMON.individualUseActionbar.set(Boolean.parseBoolean(value));
                case "individualUseSounds" -> Config.COMMON.individualUseSounds.set(Boolean.parseBoolean(value));
                case "individualUseToasts" -> Config.COMMON.individualUseToasts.set(Boolean.parseBoolean(value));
                case "structureCheckInterval" -> {
                    try { Config.COMMON.structureCheckInterval.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                }
                case "structureDamageEnabled" -> Config.COMMON.structureDamageEnabled.set(Boolean.parseBoolean(value));
                case "structureDamageAmount" -> {
                    try { Config.COMMON.structureDamageAmount.set(Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
                }
                case "structureDamageInterval" -> {
                    try { Config.COMMON.structureDamageInterval.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                }
                case "structureMessageEnabled" -> Config.COMMON.structureMessageEnabled.set(Boolean.parseBoolean(value));
                case "structureLockMessageFormat" -> Config.COMMON.structureLockMessageFormat.set(value);
                case "structureLockInChat" -> Config.COMMON.structureLockInChat.set(Boolean.parseBoolean(value));
            }
        }
    }
}
