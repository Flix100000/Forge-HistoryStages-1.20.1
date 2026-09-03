package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class ContainerClickMixin {

    private static final String FEEDBACK_CATEGORY = "container";

    /**
     * Taking an item out of a container is the same acquisition the ground-pickup handler gates,
     * so it asks the same action rather than "is this item mentioned anywhere" — otherwise an
     * entry narrowed to {@code recipe} still froze every slot holding it (Issue #117).
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void onClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!Config.GAMEPLAY.lockContainerInteraction.get()) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Validate slot index
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        Slot slot = menu.slots.get(slotId);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        if (StageLockHelper.isActionLockedByIndividualStage(stack, serverPlayer.getUUID(), "pickup")) {
            ci.cancel();

            ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Container Lock", "container_" + serverPlayer.getUUID() + "_" + itemRL,
                    "<" + serverPlayer.getName().getString() + "> Interaction with locked item '" + itemRL + "' in container blocked");

            LockFeedback.sendActionbar(serverPlayer, FEEDBACK_CATEGORY, LockMessages.itemLocked());
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
        if (!Config.GAMEPLAY.lockItemUsage.get() && !Config.GAMEPLAY.individualLockItemUsage.get()) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        Slot slot = menu.slots.get(slotId);

        boolean targetIsEquipSlot = historystages$isPlayerEquipmentSlot(slot, serverPlayer)
                || historystages$isExternalArmorSlot(slot, serverPlayer);

        ItemStack candidate = ItemStack.EMPTY;
        switch (clickType) {
            case PICKUP -> {
                if (targetIsEquipSlot) {
                    candidate = menu.getCarried();
                }
            }
            case QUICK_MOVE -> {
                if (!targetIsEquipSlot) {
                    ItemStack source = slot.getItem();
                    if (!source.isEmpty()) {
                        EquipmentSlot natural = serverPlayer.getEquipmentSlotForItem(source);
                        boolean isArmorOrOffhand = natural.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                                || natural == EquipmentSlot.OFFHAND;
                        // Block shift-click if either the vanilla armor/offhand slot is empty,
                        // or any non-vanilla ArmorSlot in the open menu (e.g. Accessories' wrapped
                        // armor slots) would accept the item.
                        if (isArmorOrOffhand
                                && (serverPlayer.getItemBySlot(natural).isEmpty()
                                    || historystages$menuHasExternalArmorTargetFor(menu, source, serverPlayer))) {
                            candidate = source;
                        }
                    }
                }
            }
            case SWAP -> {
                if (targetIsEquipSlot) {
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

        ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(candidate.getItem());
        DebugLogger.runtimeThrottled("Item Use Lock", "equip_click_" + serverPlayer.getUUID() + "_" + itemRL,
                "<" + serverPlayer.getName().getString() + "> Equip via container click for '" + itemRL + "' blocked [action: equip]");

        LockFeedback.sendActionbar(serverPlayer, FEEDBACK_CATEGORY, LockMessages.itemLocked());
    }

    @Unique
    private static boolean historystages$isPlayerEquipmentSlot(Slot slot, ServerPlayer player) {
        if (slot.container != player.getInventory()) return false;
        int idx = slot.getContainerSlot();
        return idx >= 36 && idx <= 40;
    }

    /**
     * Detects {@code ArmorSlot} instances backed by a non-vanilla container. Mods like
     * Accessories render the player's vanilla armor slots through their own UI by
     * wrapping them in a custom container, so {@link #historystages$isPlayerEquipmentSlot}
     * misses them. Anything that writes through an {@code ArmorSlot} ultimately routes
     * back to the player's vanilla equipment, so treating it as an equip target is safe.
     *
     * <p>{@code net.minecraft.world.inventory.ArmorSlot} is package-private, so we walk
     * the class hierarchy by name instead of using {@code instanceof}.
     */
    @Unique
    private static boolean historystages$isExternalArmorSlot(Slot slot, ServerPlayer player) {
        if (slot.container == player.getInventory()) return false;
        for (Class<?> c = slot.getClass(); c != null; c = c.getSuperclass()) {
            if ("net.minecraft.world.inventory.ArmorSlot".equals(c.getName())) return true;
        }
        return false;
    }

    /**
     * Returns true if any non-vanilla {@code ArmorSlot} in the open menu would accept
     * the given stack and is currently empty. Used to detect shift-click equip attempts
     * into mod-rendered armor slots whose target isn't the player's main inventory.
     */
    @Unique
    private static boolean historystages$menuHasExternalArmorTargetFor(AbstractContainerMenu menu, ItemStack stack, ServerPlayer player) {
        for (Slot s : menu.slots) {
            if (historystages$isExternalArmorSlot(s, player) && !s.hasItem() && s.mayPlace(stack)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean historystages$isEquipActionLocked(ItemStack stack, ServerPlayer player) {
        return (Config.GAMEPLAY.lockItemUsage.get()
                    && StageLockHelper.isActionLockedForPlayer(stack, player.getUUID(), "equip"))
                || (Config.GAMEPLAY.individualLockItemUsage.get()
                    && StageLockHelper.isActionLockedByIndividualStage(stack, player.getUUID(), "equip"));
    }
}
