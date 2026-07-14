package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockGate;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.minecraft.core.BlockPos;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlockLockHandler {

    private static final String FEEDBACK_CATEGORY = "block";

    /**
     * Denies right-click interaction on locked blocks (chest GUIs, doors, levers, buttons, …).
     * The "block locked" chat message is only shown for blocks that actually have a
     * MenuProvider, to avoid spamming on every right-click of plain stone/dirt.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.COMMON.lockBlockInteraction.get() && !Config.COMMON.individualLockBlockInteraction.get()) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getEntity().level().getBlockState(pos);
        Block block = state.getBlock();

        ItemStack blockItem = new ItemStack(block.asItem());
        if (blockItem.isEmpty()) return;

        boolean locked = LockGate.isActionLocked(
                blockItem, event.getEntity(), "gui",
                Config.COMMON.lockBlockInteraction,
                Config.COMMON.individualLockBlockInteraction);

        if (locked) {
            // Only deny the block's own interaction (GUI opening), not the item use.
            // setCanceled(true) would also block placing items on locked block surfaces.
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);

            // Only show the "block locked" message when the block actually has a GUI to open.
            // Blocks without a MenuProvider (plain stone, dirt, etc.) don't need a message —
            // the player was just clicking a surface, not trying to open anything.
            boolean isClient = event.getEntity().level().isClientSide();
            boolean hasGui = !isClient && state.getMenuProvider(event.getEntity().level(), pos) != null;
            if (hasGui && event.getEntity() instanceof ServerPlayer sp) {
                DebugLogger.runtimeThrottled("Block Lock", "gui_" + sp.getUUID() + "_" + ForgeRegistries.BLOCKS.getKey(block),
                        "<" + sp.getName().getString() + "> GUI open of '" + ForgeRegistries.BLOCKS.getKey(block) + "' at " + pos.toShortString() + " blocked [action: gui]");
                LockFeedback.sendActionbar(sp, FEEDBACK_CATEGORY, LockMessages.blockLocked());
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        BlockState state = event.getState();
        ItemStack blockItem = new ItemStack(state.getBlock().asItem());
        if (blockItem.isEmpty()) return;

        boolean isClient = event.getEntity().level().isClientSide();

        // Check global lock — respects lock_actions["break"]
        if (Config.COMMON.lockBlockBreaking.get()) {
            boolean globalLocked = isClient
                    ? net.bananemdnsa.historystages.util.lock.StageLockHelper.isActionLockedForClient(blockItem, "break")
                    : net.bananemdnsa.historystages.util.lock.StageLockHelper.isActionLockedForPlayer(blockItem, event.getEntity().getUUID(), "break");
            if (globalLocked) {
                float newSpeed = event.getOriginalSpeed() * Config.COMMON.lockedBlockBreakSpeedMultiplier.get().floatValue();
                event.setNewSpeed(newSpeed);
                return;
            }
        }

        // Check individual lock — respects lock_actions["break"]
        if (Config.COMMON.individualLockBlockBreaking.get()) {
            boolean individualLocked = isClient
                    ? net.bananemdnsa.historystages.util.lock.StageLockHelper.isActionLockedByIndividualStageClient(blockItem, "break")
                    : net.bananemdnsa.historystages.util.lock.StageLockHelper.isActionLockedByIndividualStage(blockItem, event.getEntity().getUUID(), "break");
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
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;

        BlockState state = event.getState();
        ItemStack blockItem = new ItemStack(state.getBlock().asItem());
        if (blockItem.isEmpty()) return;

        boolean locked = LockGate.isActionLockedServer(
                blockItem, sp, "break",
                Config.COMMON.lockBlockBreaking,
                Config.COMMON.individualLockBlockBreaking);

        if (locked) {
            event.setCanceled(true);
            DebugLogger.runtimeThrottled("Block Lock", "break_" + sp.getUUID() + "_" + state.getBlock(),
                    "<" + sp.getName().getString() + "> Break of '" + ForgeRegistries.BLOCKS.getKey(state.getBlock()) + "' at " + event.getPos().toShortString() + " blocked — no drops [action: break]");

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
