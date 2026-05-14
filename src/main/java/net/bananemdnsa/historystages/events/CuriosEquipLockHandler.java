package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CuriosEquipLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCurioEquip(CurioCanEquipEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;

        ItemStack stack = event.getStack();
        if (stack.isEmpty()) return;

        boolean isClient = player.level().isClientSide();
        if (!isItemLocked(stack, player, isClient)) return;

        event.setEquipResult(TriState.FALSE);
        if (!isClient) {
            ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtime("Curios Lock", player.getName().getString(),
                    "Equipping locked item '" + itemRL + "' in curio slot '"
                            + event.getSlotContext().identifier() + "' — blocked");
            showMessage((ServerPlayer) player);
        }
    }

    private static boolean isItemLocked(ItemStack item, Player player, boolean isClient) {
        if (Config.COMMON.lockItemUsage.get()) {
            if (isClient) {
                if (StageLockHelper.isItemLockedForClient(item)) return true;
            } else {
                if (StageLockHelper.isItemLockedForPlayer(item, player.getUUID())) return true;
            }
        }
        if (Config.COMMON.individualLockItemUsage.get()) {
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
                net.bananemdnsa.historystages.util.LockMessages.itemLocked()
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                true
        );
    }
}
