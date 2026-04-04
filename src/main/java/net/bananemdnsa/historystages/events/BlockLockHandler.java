package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlockLockHandler {

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
        if (event.getLevel().isClientSide()) return;

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
                    "<" + event.getPlayer().getName().getString() + "> Break of locked block '" + ForgeRegistries.BLOCKS.getKey(state.getBlock()) + "' at " + event.getPos().toShortString() + " — removed without drops");

            // Manually remove block without drops
            BlockPos pos = event.getPos();
            LevelAccessor level = event.getLevel();
            // Play break particles and sound
            level.levelEvent(2001, pos, Block.getId(state));
            // Remove the block (no drops)
            level.removeBlock(pos, false);
        }
    }
}
