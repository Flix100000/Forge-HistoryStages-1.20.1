package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void historystages$clicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide() || !Config.COMMON.lockContainerInteraction) {
            return;
        }
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotId < 0 || slotId >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.slots.get(slotId);
        boolean playerInventorySlot = slot.container instanceof Inventory || menu == serverPlayer.inventoryMenu;
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()
                && !playerInventorySlot
                && isLockedForAction(carried, serverPlayer, "pickup")) {
            blockClick(ci, serverPlayer, carried, "Container Lock");
            return;
        }

        ItemStack stack = slot.getItem();
        if (playerInventorySlot) {
            return;
        }
        if (stack.isEmpty() || !isLockedForAction(stack, serverPlayer, "pickup")) {
            return;
        }
        blockClick(ci, serverPlayer, stack, "Container Lock");
    }

    private static boolean isLockedForAction(ItemStack stack, ServerPlayer player, String action) {
        if (Config.COMMON.lockItemUsage && StageLockHelper.isActionLockedForPlayer(stack, player.getUUID(), action)) {
            return true;
        }
        return Config.COMMON.individualLockItemUsage
                && StageLockHelper.isActionLockedByIndividualStage(stack, player.getUUID(), action);
    }

    private static void blockClick(CallbackInfo ci, ServerPlayer serverPlayer, ItemStack stack, String logLabel) {
        ci.cancel();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        DebugLogger.runtimeThrottled(logLabel, "container_" + serverPlayer.getUUID() + "_" + id,
                "<" + serverPlayer.getName().getString() + "> Interaction with locked item '" + id + "' blocked");
        long now = System.currentTimeMillis();
        Long last = MESSAGE_COOLDOWNS.get(serverPlayer.getUUID());
        if (last == null || now - last >= COOLDOWN_MS) {
            MESSAGE_COOLDOWNS.put(serverPlayer.getUUID(), now);
            serverPlayer.displayClientMessage(Component.translatable("message.historystages.item_locked")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        }
    }
}
