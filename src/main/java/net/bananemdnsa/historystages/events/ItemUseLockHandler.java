package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        if (isItemLockedForEntity(heldItem, event.getEntity(), isClient)) {
            event.setCanceled(true);
            if (!isClient) {
                ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(heldItem.getItem());
                DebugLogger.runtimeThrottled("Item Use Lock", "use_" + event.getEntity().getUUID() + "_" + itemRL,
                        "<" + event.getEntity().getName().getString() + "> Right-click use of '" + itemRL + "' blocked");
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

        if (isItemLockedForEntity(heldItem, event.getEntity(), isClient)) {
            event.setUseItem(Event.Result.DENY);
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

        if (isItemLockedForEntity(heldItem, event.getEntity(), isClient)) {
            event.setCanceled(true);
            if (!isClient) {
                ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(heldItem.getItem());
                DebugLogger.runtimeThrottled("Item Use Lock", "mine_" + event.getEntity().getUUID() + "_" + itemRL,
                        "<" + event.getEntity().getName().getString() + "> Mining with locked tool '" + itemRL + "' blocked");
                showMessage(event.getEntity());
            }
        }
    }

    // Note: LeftClickEmpty is NOT cancelable in Forge, so we cannot prevent
    // the swing animation when left-clicking air with a locked item.
    // LeftClickBlock and AttackEntity already handle the important cases.

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

        if (isItemLockedForEntity(weapon, event.getEntity(), isClient)) {
            event.setCanceled(true);
            if (!isClient) {
                ResourceLocation weaponRL = ForgeRegistries.ITEMS.getKey(weapon.getItem());
                DebugLogger.runtimeThrottled("Item Use Lock", "attack_" + event.getEntity().getUUID() + "_" + weaponRL,
                        "<" + event.getEntity().getName().getString() + "> Attack with locked weapon '" + weaponRL + "' blocked");
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
        if (slot.getType() != EquipmentSlot.Type.ARMOR && slot != EquipmentSlot.OFFHAND) return;

        if (StageLockHelper.isItemLockedForPlayer(newItem, player)) {
            ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(newItem.getItem());
            DebugLogger.runtime("Item Use Lock", player.getName().getString(),
                    "Equipped locked item '" + itemRL + "' in slot " + slot.getName() + " — removed and returned to inventory");
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

    private static boolean isItemLockedForEntity(ItemStack item, Player player, boolean isClient) {
        if (isClient) {
            return StageLockHelper.isItemLockedForClient(item);
        } else {
            return StageLockHelper.isItemLockedForPlayer(item, player.getUUID());
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
