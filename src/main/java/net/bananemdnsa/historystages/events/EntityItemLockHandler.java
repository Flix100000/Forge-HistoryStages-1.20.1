package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
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
            if (!displayedItem.isEmpty() && StageManager.isItemLocked(displayedItem, isClient)) {
                event.setCanceled(true);
                if (!isClient) showMessage(event.getEntity());
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
            if (hasLockedItem(armorStand, isClient)) {
                event.setCanceled(true);
                if (!isClient) showMessage(event.getEntity());
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
            if (!displayedItem.isEmpty() && StageManager.isItemLocked(displayedItem, isClient)) {
                event.setCanceled(true);
                if (!isClient) showMessage(event.getEntity());
            }
        } else if (event.getTarget() instanceof ArmorStand armorStand) {
            if (hasLockedItem(armorStand, isClient)) {
                event.setCanceled(true);
                if (!isClient) showMessage(event.getEntity());
            }
        }
    }

    static boolean hasLockedItem(ArmorStand armorStand, boolean isClient) {
        for (ItemStack stack : armorStand.getArmorSlots()) {
            if (!stack.isEmpty() && StageManager.isItemLocked(stack, isClient)) {
                return true;
            }
        }
        for (ItemStack stack : armorStand.getHandSlots()) {
            if (!stack.isEmpty() && StageManager.isItemLocked(stack, isClient)) {
                return true;
            }
        }
        return false;
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
