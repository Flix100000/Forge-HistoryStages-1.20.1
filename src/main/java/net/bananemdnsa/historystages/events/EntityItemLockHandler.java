package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityItemLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    /**
     * Prevents interacting with item frames that hold locked items.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!Config.COMMON.lockEntityItems.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();

        if (event.getTarget() instanceof ItemFrame itemFrame) {
            ItemStack displayedItem = itemFrame.getItem();
            if (!displayedItem.isEmpty() && isItemLockedForContext(displayedItem, event.getEntity(), isClient)) {
                event.setCanceled(true);
                if (!isClient) {
                    DebugLogger.runtimeThrottled("Entity Item Lock", "frame_interact_" + event.getEntity().getUUID(),
                            "<" + event.getEntity().getName().getString() + "> Interaction with item frame (locked item) blocked");
                    showMessage(event.getEntity());
                }
            }
        }
    }

    /**
     * Prevents slot-specific interactions with armor stands that hold locked items.
     * EntityInteractSpecific is used because armor stand slot selection depends on
     * the exact position the player is looking at (head, body, legs, feet, hand).
     */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!Config.COMMON.lockEntityItems.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();

        if (event.getTarget() instanceof ArmorStand armorStand) {
            if (hasLockedItem(armorStand, event.getEntity(), isClient)) {
                event.setCanceled(true);
                if (!isClient) {
                    DebugLogger.runtimeThrottled("Entity Item Lock", "stand_interact_" + event.getEntity().getUUID(),
                            "<" + event.getEntity().getName().getString() + "> Interaction with armor stand (locked item) blocked");
                    showMessage(event.getEntity());
                }
            }
        }
    }

    /**
     * Prevents breaking armor stands or item frames that hold locked items.
     * This stops players from destroying the entity to get the locked items as drops.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!Config.COMMON.lockEntityItems.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();

        if (event.getTarget() instanceof ItemFrame itemFrame) {
            ItemStack displayedItem = itemFrame.getItem();
            if (!displayedItem.isEmpty() && isItemLockedForContext(displayedItem, event.getEntity(), isClient)) {
                event.setCanceled(true);
                if (!isClient) {
                    DebugLogger.runtimeThrottled("Entity Item Lock", "frame_attack_" + event.getEntity().getUUID(),
                            "<" + event.getEntity().getName().getString() + "> Attack on item frame (locked item) blocked");
                    showMessage(event.getEntity());
                }
            }
        } else if (event.getTarget() instanceof ArmorStand armorStand) {
            if (hasLockedItem(armorStand, event.getEntity(), isClient)) {
                event.setCanceled(true);
                if (!isClient) {
                    DebugLogger.runtimeThrottled("Entity Item Lock", "stand_attack_" + event.getEntity().getUUID(),
                            "<" + event.getEntity().getName().getString() + "> Attack on armor stand (locked item) blocked");
                    showMessage(event.getEntity());
                }
            }
        }
    }

    static boolean hasLockedItem(ArmorStand armorStand, Player player, boolean isClient) {
        for (ItemStack stack : armorStand.getArmorSlots()) {
            if (!stack.isEmpty() && isItemLockedForContext(stack, player, isClient)) {
                return true;
            }
        }
        for (ItemStack stack : armorStand.getHandSlots()) {
            if (!stack.isEmpty() && isItemLockedForContext(stack, player, isClient)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isItemLockedForContext(ItemStack item, Player player, boolean isClient) {
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
                Component.translatable("message.historystages.entity_item_locked")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                true
        );
    }
}
