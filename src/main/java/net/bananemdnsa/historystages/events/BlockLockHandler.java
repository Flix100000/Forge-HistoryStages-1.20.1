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
     * Denies right-click interaction on locked blocks (chest GUIs, doors, levers, buttons, …).
     * The "block locked" chat message is only shown for blocks that actually have a
     * MenuProvider, to avoid spamming on every right-click of plain stone/dirt.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.COMMON.lockBlockInteraction.get() && !Config.COMMON.individualLockBlockInteraction.get()) return;

        boolean isClient = event.getEntity().level().isClientSide();
        BlockPos pos = event.getPos();
        BlockState state = event.getEntity().level().getBlockState(pos);
        Block block = state.getBlock();

        ItemStack blockItem = new ItemStack(block.asItem());
        if (blockItem.isEmpty()) return;

        boolean locked = false;

        // Check global lock — respects lock_actions["gui"]
        if (Config.COMMON.lockBlockInteraction.get()) {
            if (isClient) {
                locked = StageLockHelper.isActionLockedForClient(blockItem, "gui");
            } else {
                locked = StageLockHelper.isActionLockedForPlayer(blockItem, event.getEntity().getUUID(), "gui");
            }
        }

        // Check individual lock — respects lock_actions["gui"]
        if (!locked && Config.COMMON.individualLockBlockInteraction.get()) {
            if (isClient) {
                locked = StageLockHelper.isActionLockedByIndividualStageClient(blockItem, "gui");
            } else {
                locked = StageLockHelper.isActionLockedByIndividualStage(blockItem, event.getEntity().getUUID(), "gui");
            }
        }

        if (locked) {
            // Only deny the block's own interaction (GUI opening), not the item use.
            // setCanceled(true) would also block placing items on locked block surfaces.
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);

            // Only show the "block locked" message when the block actually has a GUI to open.
            // Blocks without a MenuProvider (plain stone, dirt, etc.) don't need a message —
            // the player was just clicking a surface, not trying to open anything.
            boolean hasGui = !isClient && state.getMenuProvider(event.getEntity().level(), pos) != null;
            if (hasGui && event.getEntity() instanceof ServerPlayer sp) {
                DebugLogger.runtimeThrottled("Block Lock", "gui_" + sp.getUUID() + "_" + BuiltInRegistries.BLOCK.getKey(block),
                        "<" + sp.getName().getString() + "> GUI open of '" + BuiltInRegistries.BLOCK.getKey(block) + "' at " + pos.toShortString() + " blocked [action: gui]");

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

        // Check global lock — respects lock_actions["break"]
        if (Config.COMMON.lockBlockBreaking.get()) {
            boolean globalLocked;
            if (isClient) {
                globalLocked = StageLockHelper.isActionLockedForClient(blockItem, "break");
            } else {
                globalLocked = StageLockHelper.isActionLockedForPlayer(blockItem, event.getEntity().getUUID(), "break");
            }
            if (globalLocked) {
                float newSpeed = event.getOriginalSpeed() * Config.COMMON.lockedBlockBreakSpeedMultiplier.get().floatValue();
                event.setNewSpeed(newSpeed);
                return;
            }
        }

        // Check individual lock — respects lock_actions["break"]
        if (Config.COMMON.individualLockBlockBreaking.get()) {
            boolean individualLocked;
            if (isClient) {
                individualLocked = StageLockHelper.isActionLockedByIndividualStageClient(blockItem, "break");
            } else {
                individualLocked = StageLockHelper.isActionLockedByIndividualStage(blockItem, event.getEntity().getUUID(), "break");
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

        // Check global lock — respects lock_actions["break"]: if break is explicitly allowed, the block can be broken
        if (Config.COMMON.lockBlockBreaking.get() && StageLockHelper.isActionLockedForPlayer(blockItem, event.getPlayer().getUUID(), "break")) {
            locked = true;
        }

        // Check individual lock
        if (!locked && Config.COMMON.individualLockBlockBreaking.get() && StageLockHelper.isActionLockedByIndividualStage(blockItem, event.getPlayer().getUUID(), "break")) {
            locked = true;
        }

        if (locked) {
            event.setCanceled(true);
            DebugLogger.runtimeThrottled("Block Lock", "break_" + event.getPlayer().getUUID() + "_" + state.getBlock(),
                    "<" + event.getPlayer().getName().getString() + "> Break of '" + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "' at " + event.getPos().toShortString() + " blocked — no drops [action: break]");

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
