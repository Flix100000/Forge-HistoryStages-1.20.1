package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class ItemUseLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;
    private static boolean suppressEquipmentCheck = false;

    /**
     * Prevents using locked items (eating, drinking, bows, shields, etc.)
     * Cancelled on BOTH sides to prevent animations and item consumption.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!Config.COMMON.lockItemUsage.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();
        ItemStack heldItem = event.getItemStack();
        if (heldItem.isEmpty()) return;

        if (StageManager.isItemLocked(heldItem, isClient)) {
            event.setCanceled(true);
            if (!isClient) {
                showMessage(event.getEntity());
            }
        }
    }

    /**
     * Prevents using locked items on blocks (placing, tilling, etc.)
     * but still allows block interaction (opening chests, crafting tables).
     * Cancelled on BOTH sides to prevent ghost blocks and item consumption.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.COMMON.lockItemUsage.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();
        ItemStack heldItem = event.getItemStack();
        if (heldItem.isEmpty()) return;

        if (StageManager.isItemLocked(heldItem, isClient)) {
            event.setUseItem(TriState.FALSE);
        }
    }

    /**
     * Prevents mining/breaking blocks with a locked tool in hand.
     * Cancelled on BOTH sides to prevent mining animation.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!Config.COMMON.lockItemUsage.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();
        ItemStack heldItem = event.getItemStack();
        if (heldItem.isEmpty()) return;

        if (StageManager.isItemLocked(heldItem, isClient)) {
            event.setCanceled(true);
            if (!isClient) {
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
        if (!Config.COMMON.lockItemUsage.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();
        ItemStack weapon = event.getEntity().getMainHandItem();
        if (weapon.isEmpty()) return;

        if (StageManager.isItemLocked(weapon, isClient)) {
            event.setCanceled(true);
            if (!isClient) {
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
        if (!Config.COMMON.lockItemUsage.get()) return;

        ItemStack newItem = event.getTo();
        if (newItem.isEmpty()) return;

        EquipmentSlot slot = event.getSlot();
        // Only handle armor and offhand slots — players can still hold locked items in main hand
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR && slot != EquipmentSlot.OFFHAND) return;

        if (StageManager.isItemLockedForServer(newItem)) {
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

    private static void showMessage(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;

        long now = System.currentTimeMillis();
        Long last = MESSAGE_COOLDOWNS.get(sp.getUUID());
        if (last != null && (now - last) < COOLDOWN_MS) return;
        MESSAGE_COOLDOWNS.put(sp.getUUID(), now);

        sp.displayClientMessage(
                Component.translatable("message.historystages.item_locked")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                true
        );
    }
}
