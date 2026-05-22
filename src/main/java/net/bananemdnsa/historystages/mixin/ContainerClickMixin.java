package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(AbstractContainerMenu.class)
public class ContainerClickMixin {

    private static final Map<UUID, Long> CONTAINER_MSG_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void onClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!Config.COMMON.lockContainerInteraction.get()) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Validate slot index
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        Slot slot = menu.slots.get(slotId);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        if (StageLockHelper.isItemLockedByIndividualStage(stack, serverPlayer.getUUID())) {
            ci.cancel();

            ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Container Lock", "container_" + serverPlayer.getUUID() + "_" + itemRL,
                    "<" + serverPlayer.getName().getString() + "> Interaction with locked item '" + itemRL + "' in container blocked");

            long now = System.currentTimeMillis();
            Long last = CONTAINER_MSG_COOLDOWNS.get(serverPlayer.getUUID());
            if (last == null || (now - last) >= COOLDOWN_MS) {
                CONTAINER_MSG_COOLDOWNS.put(serverPlayer.getUUID(), now);
                serverPlayer.displayClientMessage(
                        Component.translatable("message.historystages.item_locked")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                        true
                );
            }
        }
    }

    /**
     * Blocks container clicks that would equip an item locked by the "equip" action
     * (armor and offhand slots). Catches rapid-fire clicks from helper mods like
     * Mouse Tweaks that bypass the post-hoc {@code LivingEquipmentChangeEvent} revert
     * by racing the client-side prediction.
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void historystages$onEquipLockClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        Slot slot = menu.slots.get(slotId);

        ItemStack candidate = ItemStack.EMPTY;
        switch (clickType) {
            case PICKUP -> {
                if (historystages$isPlayerEquipmentSlot(slot, serverPlayer)) {
                    candidate = menu.getCarried();
                }
            }
            case QUICK_MOVE -> {
                if (menu instanceof InventoryMenu
                        && !historystages$isPlayerEquipmentSlot(slot, serverPlayer)) {
                    ItemStack source = slot.getItem();
                    EquipmentSlot natural = serverPlayer.getEquipmentSlotForItem(source);
                    if (!source.isEmpty()
                            && (natural.getType() == EquipmentSlot.Type.ARMOR || natural == EquipmentSlot.OFFHAND)
                            && serverPlayer.getItemBySlot(natural).isEmpty()) {
                        candidate = source;
                    }
                }
            }
            case SWAP -> {
                if (historystages$isPlayerEquipmentSlot(slot, serverPlayer)) {
                    Inventory inv = serverPlayer.getInventory();
                    if (button == 40) {
                        candidate = inv.offhand.get(0);
                    } else if (button >= 0 && button < inv.items.size()) {
                        candidate = inv.items.get(button);
                    }
                }
            }
            default -> { return; }
        }

        if (candidate.isEmpty()) return;
        if (!historystages$isEquipActionLocked(candidate, serverPlayer)) return;

        ci.cancel();

        ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(candidate.getItem());
        DebugLogger.runtimeThrottled("Item Use Lock", "equip_click_" + serverPlayer.getUUID() + "_" + itemRL,
                "<" + serverPlayer.getName().getString() + "> Equip via container click for '" + itemRL + "' blocked [action: equip]");

        long now = System.currentTimeMillis();
        Long last = CONTAINER_MSG_COOLDOWNS.get(serverPlayer.getUUID());
        if (last == null || (now - last) >= COOLDOWN_MS) {
            CONTAINER_MSG_COOLDOWNS.put(serverPlayer.getUUID(), now);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.historystages.item_locked")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                    true
            );
        }
    }

    @Unique
    private static boolean historystages$isPlayerEquipmentSlot(Slot slot, ServerPlayer player) {
        if (slot.container != player.getInventory()) return false;
        int idx = slot.getContainerSlot();
        return idx >= 36 && idx <= 40;
    }

    @Unique
    private static boolean historystages$isEquipActionLocked(ItemStack stack, ServerPlayer player) {
        return (Config.COMMON.lockItemUsage.get()
                    && StageLockHelper.isActionLockedForPlayer(stack, player.getUUID(), "equip"))
                || (Config.COMMON.individualLockItemUsage.get()
                    && StageLockHelper.isActionLockedByIndividualStage(stack, player.getUUID(), "equip"));
    }
}
