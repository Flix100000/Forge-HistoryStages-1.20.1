package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.LockFeedback;
import net.bananemdnsa.historystages.util.LockGate;
import net.bananemdnsa.historystages.util.LockMessages;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class ItemUseLockHandler {

    private static final String FEEDBACK_CATEGORY = "item";
    private static boolean suppressEquipmentCheck = false;

    /**
     * Prevents using locked items (eating, drinking, bows, shields, etc.)
     * Cancelled on BOTH sides to prevent animations and item consumption.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;

        ItemStack heldItem = event.getItemStack();
        if (heldItem.isEmpty()) return;

        if (isActionLocked(heldItem, event.getEntity(), "use")) {
            event.setCanceled(true);
            if (!event.getEntity().level().isClientSide()) {
                ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(heldItem.getItem());
                DebugLogger.runtimeThrottled("Item Use Lock", "use_" + event.getEntity().getUUID() + "_" + itemRL,
                        "<" + event.getEntity().getName().getString() + "> Use of '" + itemRL + "' blocked [action: use]");
                showMessage(event.getEntity());
            }
        }
    }

    /**
     * Prevents using locked items on blocks (placing, tilling, etc.)
     * but still allows block interaction (opening chests, crafting tables).
     * Also prevents consumables (food, potions) from being used while looking at a block.
     * Cancelled on BOTH sides to prevent ghost blocks and item consumption.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;

        ItemStack heldItem = event.getItemStack();
        if (heldItem.isEmpty()) return;

        if (isActionLocked(heldItem, event.getEntity(), "place")
                || isActionLocked(heldItem, event.getEntity(), "use")) {
            event.setUseItem(TriState.FALSE);
        }
    }

    /**
     * Prevents mining/breaking blocks with a locked tool in hand.
     * Cancelled on BOTH sides to prevent mining animation.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;

        ItemStack heldItem = event.getItemStack();
        if (heldItem.isEmpty()) return;

        if (isActionLocked(heldItem, event.getEntity(), "break")) {
            event.setCanceled(true);
            if (!event.getEntity().level().isClientSide()) {
                ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(heldItem.getItem());
                DebugLogger.runtimeThrottled("Item Use Lock", "break_" + event.getEntity().getUUID() + "_" + itemRL,
                        "<" + event.getEntity().getName().getString() + "> Mining with '" + itemRL + "' blocked [action: break]");
                showMessage(event.getEntity());
            }
        }
    }

    /**
     * Prevents attacking entities with a locked weapon/tool in hand.
     * Empty hand (fist) attacks are not affected.
     * Cancelled on BOTH sides to prevent swing animations.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;

        ItemStack weapon = event.getEntity().getMainHandItem();
        if (weapon.isEmpty()) return;

        if (isActionLocked(weapon, event.getEntity(), "attack")) {
            event.setCanceled(true);
            if (!event.getEntity().level().isClientSide()) {
                ResourceLocation weaponRL = BuiltInRegistries.ITEM.getKey(weapon.getItem());
                DebugLogger.runtimeThrottled("Item Use Lock", "attack_" + event.getEntity().getUUID() + "_" + weaponRL,
                        "<" + event.getEntity().getName().getString() + "> Attack with '" + weaponRL + "' blocked [action: attack]");
                showMessage(event.getEntity());
            }
        }
    }

    /**
     * Prevents equipping locked items in armor or offhand slots.
     * If a locked item is equipped, it is removed and returned to the inventory (or dropped).
     * Server-side only — the server corrects the client state automatically.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (suppressEquipmentCheck) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;

        ItemStack newItem = event.getTo();
        if (newItem.isEmpty()) return;

        EquipmentSlot slot = event.getSlot();
        // Only handle armor and offhand slots — players can still hold locked items in main hand
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR && slot != EquipmentSlot.OFFHAND) return;

        boolean locked = LockGate.isActionLockedServer(
                newItem, player, "equip",
                Config.COMMON.lockItemUsage,
                Config.COMMON.individualLockItemUsage);
        if (locked) {
            ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(newItem.getItem());
            DebugLogger.runtime("Item Use Lock", player.getName().getString(),
                    "'" + itemRL + "' in slot " + slot.getName() + " removed and returned to inventory [action: equip]");
            suppressEquipmentCheck = true;
            try {
                player.setItemSlot(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(newItem.copy())) {
                    player.drop(newItem.copy(), false);
                }
                // Sync inventory to client so it sees the correction
                player.containerMenu.broadcastChanges();
            } finally {
                suppressEquipmentCheck = false;
            }
            showMessage(player);
        }
    }

    private static boolean isActionLocked(ItemStack item, Player player, String action) {
        return LockGate.isActionLocked(item, player, action,
                Config.COMMON.lockItemUsage,
                Config.COMMON.individualLockItemUsage);
    }

    private static void showMessage(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        LockFeedback.sendActionbar(sp, FEEDBACK_CATEGORY, LockMessages.itemLocked());
    }
}
