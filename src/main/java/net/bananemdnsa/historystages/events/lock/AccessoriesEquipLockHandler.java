package net.bananemdnsa.historystages.events.lock;

import io.wispforest.accessories.api.events.CanEquipCallback;
import io.wispforest.accessories.api.slot.SlotReference;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AccessoriesEquipLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    private AccessoriesEquipLockHandler() {}

    public static void register() {
        CanEquipCallback.EVENT.register(AccessoriesEquipLockHandler::canEquip);
    }

    private static TriState canEquip(ItemStack stack, SlotReference reference) {
        if (stack.isEmpty()) return TriState.DEFAULT;
        if (!Config.GAMEPLAY.lockItemUsage.get() && !Config.GAMEPLAY.individualLockItemUsage.get()) return TriState.DEFAULT;

        LivingEntity entity = reference.entity();
        if (!(entity instanceof Player player)) return TriState.DEFAULT;

        boolean isClient = player.level().isClientSide();
        if (!isItemLocked(stack, player, isClient)) return TriState.DEFAULT;

        if (!isClient) {
            ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtime("Accessories Lock", player.getName().getString(),
                    "Equipping locked item '" + itemRL + "' in accessory slot '"
                            + reference.slotName() + "' — blocked");
            showMessage((ServerPlayer) player);
        }
        return TriState.FALSE;
    }

    private static boolean isItemLocked(ItemStack item, Player player, boolean isClient) {
        if (Config.GAMEPLAY.lockItemUsage.get()) {
            if (isClient) {
                if (StageLockHelper.isItemLockedForClient(item)) return true;
            } else {
                if (StageLockHelper.isItemLockedForPlayer(item, player.getUUID())) return true;
            }
        }
        if (Config.GAMEPLAY.individualLockItemUsage.get()) {
            if (isClient) {
                if (StageLockHelper.isItemLockedByIndividualStageClient(item)) return true;
            } else {
                if (StageLockHelper.isItemLockedByIndividualStage(item, player.getUUID())) return true;
            }
        }
        return false;
    }

    private static void showMessage(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = MESSAGE_COOLDOWNS.get(player.getUUID());
        if (last != null && (now - last) < COOLDOWN_MS) return;
        MESSAGE_COOLDOWNS.put(player.getUUID(), now);

        player.displayClientMessage(
                net.bananemdnsa.historystages.util.lock.LockMessages.itemLocked()
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                true
        );
    }
}
