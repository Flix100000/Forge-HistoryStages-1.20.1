package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class BlockLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    /**
     * Prevents opening the GUI of locked blocks (chests, furnaces, crafting tables, etc.)
     * Only blocks that have a MenuProvider (i.e., a GUI) are affected.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.COMMON.lockBlockInteraction.get() && !Config.COMMON.individualLockBlockInteraction.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();
        BlockPos pos = event.getPos();
        BlockState state = event.getEntity().level().getBlockState(pos);
        Block block = state.getBlock();

        // Only care about blocks that have a GUI (MenuProvider)
        if (!(block instanceof MenuProvider) && !(event.getEntity().level().getBlockEntity(pos) instanceof MenuProvider)) return;

        ItemStack blockItem = new ItemStack(block.asItem());
        if (blockItem.isEmpty()) return;

        boolean locked = false;

        // Check global lock
        if (Config.COMMON.lockBlockInteraction.get()) {
            if (isClient) {
                locked = StageLockHelper.isItemLockedForClient(blockItem);
            } else {
                locked = StageLockHelper.isItemLockedForPlayer(blockItem, event.getEntity().getUUID());
            }
        }

        // Check individual lock
        if (!locked && Config.COMMON.individualLockBlockInteraction.get()) {
            if (isClient) {
                locked = StageLockHelper.isItemLockedByIndividualStageClient(blockItem);
            } else {
                locked = StageLockHelper.isItemLockedByIndividualStage(blockItem, event.getEntity().getUUID());
            }
        }

        if (locked) {
            event.setCanceled(true);
            if (!isClient && event.getEntity() instanceof ServerPlayer sp) {
                DebugLogger.runtimeThrottled("Block Lock", "interact_" + sp.getUUID() + "_" + BuiltInRegistries.BLOCK.getKey(block),
                        "<" + sp.getName().getString() + "> GUI open of locked block '" + BuiltInRegistries.BLOCK.getKey(block) + "' at " + pos.toShortString() + " blocked");

                long now = System.currentTimeMillis();
                Long last = MESSAGE_COOLDOWNS.get(sp.getUUID());
                if (last == null || (now - last) >= COOLDOWN_MS) {
                    MESSAGE_COOLDOWNS.put(sp.getUUID(), now);
                    sp.displayClientMessage(
                            Component.translatable("message.historystages.block_locked")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                            true
                    );
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        BlockState state = event.getState();
        ItemStack blockItem = new ItemStack(state.getBlock().asItem());
        if (blockItem.isEmpty()) return;

        boolean isClient = event.getEntity().level().isClientSide();

        // Check global lock
        if (Config.COMMON.lockBlockBreaking.get()) {
            boolean globalLocked;
            if (isClient) {
                globalLocked = StageLockHelper.isItemLockedForClient(blockItem);
            } else {
                globalLocked = StageLockHelper.isItemLockedForPlayer(blockItem, event.getEntity().getUUID());
            }
            if (globalLocked) {
                float newSpeed = event.getOriginalSpeed() * Config.COMMON.lockedBlockBreakSpeedMultiplier.get().floatValue();
                event.setNewSpeed(newSpeed);
                return;
            }
        }

        // Check individual lock
        if (Config.COMMON.individualLockBlockBreaking.get()) {
            boolean individualLocked;
            if (isClient) {
                individualLocked = StageLockHelper.isItemLockedByIndividualStageClient(blockItem);
            } else {
                individualLocked = StageLockHelper.isItemLockedByIndividualStage(blockItem, event.getEntity().getUUID());
            }
            if (individualLocked) {
                float newSpeed = event.getOriginalSpeed() * Config.COMMON.individualLockedBlockBreakSpeedMultiplier.get().floatValue();
                event.setNewSpeed(newSpeed);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        BlockState state = event.getState();
        ItemStack blockItem = new ItemStack(state.getBlock().asItem());
        if (blockItem.isEmpty()) return;

        boolean locked = false;

        // Check global lock
        if (Config.COMMON.lockBlockBreaking.get() && StageLockHelper.isItemLockedForPlayer(blockItem, event.getPlayer().getUUID())) {
            locked = true;
        }

        // Check individual lock
        if (!locked && Config.COMMON.individualLockBlockBreaking.get() && StageLockHelper.isItemLockedByIndividualStage(blockItem, event.getPlayer().getUUID())) {
            locked = true;
        }

        if (locked) {
            event.setCanceled(true);
            DebugLogger.runtimeThrottled("Block Lock", "block_" + event.getPlayer().getUUID() + "_" + state.getBlock(),
                    "<" + event.getPlayer().getName().getString() + "> Break of locked block '" + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "' at " + event.getPos().toShortString() + " — removed without drops");

            // Manually remove block without drops
            BlockPos pos = event.getPos();
            LevelAccessor access = event.getLevel();
            // Play break particles and sound
            access.levelEvent(2001, pos, Block.getId(state));
            // Remove the block (no drops)
            access.removeBlock(pos, false);
        }
    }
}
