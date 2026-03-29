package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.core.BlockPos;
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
        if (!Config.COMMON.lockBlockBreaking.get()) return;

        BlockState state = event.getState();
        ItemStack blockItem = new ItemStack(state.getBlock().asItem());

        boolean isClient = event.getEntity().level().isClientSide();
        if (!blockItem.isEmpty() && StageManager.isItemLocked(blockItem, isClient)) {
            float newSpeed = event.getOriginalSpeed() * Config.COMMON.lockedBlockBreakSpeedMultiplier.get().floatValue();
            event.setNewSpeed(newSpeed);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!Config.COMMON.lockBlockBreaking.get()) return;

        BlockState state = event.getState();
        ItemStack blockItem = new ItemStack(state.getBlock().asItem());

        if (!blockItem.isEmpty() && StageManager.isItemLockedForServer(blockItem)) {
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
