package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class GameplayEvents {
    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, String> PORTAL_TOUCH_TARGETS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    private GameplayEvents() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (shouldBlockHeldItem(stack, player)) {
                showMessage(player, "message.historystages.item_locked");
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack held = player.getItemInHand(hand);
            if (shouldBlockHeldItem(held, player)) {
                showMessage(player, "message.historystages.item_locked");
                return InteractionResult.FAIL;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (hasMenu(state.getBlock(), blockEntity) && shouldBlockBlock(state, player)) {
                showMessage(player, "message.historystages.block_locked");
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (shouldBlockHeldItem(player.getItemInHand(hand), player)) {
                showMessage(player, "message.historystages.item_locked");
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack held = player.getItemInHand(hand);
            if (shouldBlockHeldItem(held, player)) {
                showMessage(player, "message.historystages.item_locked");
                return InteractionResult.FAIL;
            }

            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (entityId != null && StageLockHelper.isEntityAttackLockedForPlayer(entityId.toString(), player.getUUID())) {
                showMessage(player, "message.historystages.mob_unknown");
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (shouldBlockBlock(state, player)) {
                showMessage(player, "message.historystages.block_locked");
                if (!world.isClientSide()) {
                    world.levelEvent(2001, pos, Block.getId(state));
                    world.removeBlock(pos, false);
                }
                return false;
            }
            return true;
        });

        ServerEntityEvents.EQUIPMENT_CHANGE.register((livingEntity, slot, previousStack, currentStack) -> {
            if (!(livingEntity instanceof ServerPlayer player) || currentStack.isEmpty()) {
                return;
            }
            if ((slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR && slot != EquipmentSlot.OFFHAND)
                    || !shouldBlockHeldItem(currentStack, player)) {
                return;
            }
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(currentStack.copy())) {
                player.drop(currentStack.copy(), false);
            }
            player.containerMenu.broadcastChanges();
            showMessage(player, "message.historystages.item_locked");
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                return true;
            }
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return entityId == null || !StageLockHelper.isEntityAttackLockedForPlayer(entityId.toString(), player.getUUID());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String lockedPortalTarget = getLockedPortalTarget(player);
                if (lockedPortalTarget != null) {
                    String previous = PORTAL_TOUCH_TARGETS.put(player.getUUID(), lockedPortalTarget);
                    if (!lockedPortalTarget.equals(previous)) {
                        showDimensionLockedMessage(player);
                    }
                } else {
                    PORTAL_TOUCH_TARGETS.remove(player.getUUID());
                }
            }
            DebugLogger.cleanupThrottleMap();
        });
    }

    public static boolean shouldBlockDimensionChange(ServerPlayer player, String targetDimensionId) {
        return StageLockHelper.isDimensionLockedForPlayer(targetDimensionId, player.getUUID());
    }

    public static void showDimensionLockedMessage(Player player) {
        showMessage(player, "message.historystages.dimension_unknown");
    }

    private static boolean shouldBlockHeldItem(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return false;
        }
        if (Config.COMMON.lockItemUsage) {
            if (player.level().isClientSide()) {
                if (StageLockHelper.isItemLockedForClient(stack)) {
                    return true;
                }
            } else if (player instanceof ServerPlayer serverPlayer
                    && StageLockHelper.isItemLockedForPlayer(stack, serverPlayer)) {
                return true;
            }
        }
        if (!Config.COMMON.individualLockItemUsage) {
            return false;
        }
        if (player.level().isClientSide()) {
            return StageLockHelper.isItemLockedByIndividualStageClient(stack);
        }
        return StageLockHelper.isItemLockedByIndividualStage(stack, player.getUUID());
    }

    private static boolean shouldBlockBlock(BlockState state, Player player) {
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (stack.isEmpty()) {
            return false;
        }
        if (Config.COMMON.lockBlockInteraction || Config.COMMON.lockBlockBreaking) {
            if (player.level().isClientSide()) {
                if (StageLockHelper.isItemLockedForClient(stack)) {
                    return true;
                }
            } else if (player instanceof ServerPlayer serverPlayer && StageLockHelper.isItemLockedForPlayer(stack, serverPlayer)) {
                return true;
            }
        }
        if (!(Config.COMMON.individualLockBlockInteraction || Config.COMMON.individualLockBlockBreaking)) {
            return false;
        }
        if (player.level().isClientSide()) {
            return StageLockHelper.isItemLockedByIndividualStageClient(stack);
        }
        return StageLockHelper.isItemLockedByIndividualStage(stack, player.getUUID());
    }

    private static boolean hasMenu(Block block, BlockEntity blockEntity) {
        return block instanceof net.minecraft.world.MenuProvider || blockEntity instanceof net.minecraft.world.MenuProvider;
    }

    private static String getLockedPortalTarget(ServerPlayer player) {
        if (touchesPortal(player, state -> state.getBlock() instanceof NetherPortalBlock)) {
            String target = player.serverLevel().dimension() == Level.NETHER
                    ? Level.OVERWORLD.location().toString()
                    : Level.NETHER.location().toString();
            return StageLockHelper.isDimensionLockedForPlayer(target, player.getUUID()) ? target : null;
        }

        if (touchesPortal(player, state -> state.getBlock() instanceof EndPortalBlock)) {
            String target = player.serverLevel().dimension() == Level.END
                    ? Level.OVERWORLD.location().toString()
                    : Level.END.location().toString();
            return StageLockHelper.isDimensionLockedForPlayer(target, player.getUUID()) ? target : null;
        }

        return null;
    }

    private static boolean touchesPortal(Entity entity, Predicate<BlockState> matcher) {
        AABB box = entity.getBoundingBox().inflate(-1.0E-4D);
        int minX = net.minecraft.util.Mth.floor(box.minX);
        int maxX = net.minecraft.util.Mth.floor(box.maxX);
        int minY = net.minecraft.util.Mth.floor(box.minY);
        int maxY = net.minecraft.util.Mth.floor(box.maxY);
        int minZ = net.minecraft.util.Mth.floor(box.minZ);
        int maxZ = net.minecraft.util.Mth.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (matcher.test(entity.level().getBlockState(new BlockPos(x, y, z)))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void showMessage(Player player, String translationKey) {
        long now = System.currentTimeMillis();
        Long last = MESSAGE_COOLDOWNS.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        MESSAGE_COOLDOWNS.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(translationKey)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
    }
}
