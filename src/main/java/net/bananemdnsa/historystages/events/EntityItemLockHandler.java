package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EntityItemLockHandler {
    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    private EntityItemLockHandler() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            boolean isClient = world.isClientSide();

            ItemStack held = player.getItemInHand(hand);
            if (!held.isEmpty() && isItemLockedForContext(held, player, isClient)) {
                if (!isClient) {
                    DebugLogger.runtimeThrottled("Entity Item Lock", "use_held_" + player.getUUID(),
                            "<" + player.getName().getString() + "> Used locked item on entity blocked");
                    showHeldMessage(player);
                }
                return InteractionResult.FAIL;
            }

            if (!Config.COMMON.lockEntityItems) return InteractionResult.PASS;

            if (entity instanceof ItemFrame frame) {
                ItemStack displayed = frame.getItem();
                if (!displayed.isEmpty() && isItemLockedForContext(displayed, player, isClient)) {
                    if (!isClient) {
                        DebugLogger.runtimeThrottled("Entity Item Lock", "frame_interact_" + player.getUUID(),
                                "<" + player.getName().getString() + "> Interaction with item frame (locked item) blocked");
                        showMessage(player);
                    }
                    return InteractionResult.FAIL;
                }
            } else if (entity instanceof ArmorStand stand) {
                if (hasLockedItem(stand, player, isClient)) {
                    if (!isClient) {
                        DebugLogger.runtimeThrottled("Entity Item Lock", "stand_interact_" + player.getUUID(),
                                "<" + player.getName().getString() + "> Interaction with armor stand (locked item) blocked");
                        showMessage(player);
                    }
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!Config.COMMON.lockEntityItems) return InteractionResult.PASS;
            boolean isClient = world.isClientSide();

            if (entity instanceof ItemFrame frame) {
                ItemStack displayed = frame.getItem();
                if (!displayed.isEmpty() && isItemLockedForContext(displayed, player, isClient)) {
                    if (!isClient) {
                        DebugLogger.runtimeThrottled("Entity Item Lock", "frame_attack_" + player.getUUID(),
                                "<" + player.getName().getString() + "> Attack on item frame (locked item) blocked");
                        showMessage(player);
                    }
                    return InteractionResult.FAIL;
                }
            } else if (entity instanceof ArmorStand stand) {
                if (hasLockedItem(stand, player, isClient)) {
                    if (!isClient) {
                        DebugLogger.runtimeThrottled("Entity Item Lock", "stand_attack_" + player.getUUID(),
                                "<" + player.getName().getString() + "> Attack on armor stand (locked item) blocked");
                        showMessage(player);
                    }
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean hasLockedItem(ArmorStand armorStand, Player player, boolean isClient) {
        for (ItemStack stack : armorStand.getArmorSlots()) {
            if (!stack.isEmpty() && isItemLockedForContext(stack, player, isClient)) return true;
        }
        for (ItemStack stack : armorStand.getHandSlots()) {
            if (!stack.isEmpty() && isItemLockedForContext(stack, player, isClient)) return true;
        }
        return false;
    }

    private static boolean isItemLockedForContext(ItemStack item, Player player, boolean isClient) {
        if (isClient) {
            return StageLockHelper.isItemLockedForClient(item);
        }
        return StageLockHelper.isItemLockedForPlayer(item, player.getUUID());
    }

    private static void showHeldMessage(Player player) {
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
