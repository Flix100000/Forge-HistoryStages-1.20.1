package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.util.lock.LockGate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks for an open scroll back off a lectern. Everything is re-checked server-side; the
 * button the reader sees is a display, not an authority.
 */
public class TakeLecternScrollPacket {
    private final BlockPos pos;

    public TakeLecternScrollPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(TakeLecternScrollPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static TakeLecternScrollPacket decode(FriendlyByteBuf buf) {
        return new TakeLecternScrollPacket(buf.readBlockPos());
    }

    public static void handle(TakeLecternScrollPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Adventure mode reads but does not loot, exactly as vanilla gates its take-book button.
            if (!player.mayBuild()) return;

            // Reach check: refuse anything the player could not plausibly be using.
            Level level = player.level();
            if (!level.isLoaded(packet.pos)) return;
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 64.0) return;

            BlockState state = level.getBlockState(packet.pos);
            if (!(state.getBlock() instanceof LecternBlock)) return;
            if (!state.getValue(LecternBlock.HAS_BOOK)) return;
            if (!(level.getBlockEntity(packet.pos) instanceof LecternBlockEntity lectern)) return;

            ItemStack stack = lectern.getBook().copy();
            if (!stack.is(ModItems.RESEARCH_SCROLL_OPEN.get())) return;

            // Mirror BlockLockHandler.onRightClickBlock: a locked lectern denies its GUI on the
            // open path, so a forged take packet must not be able to loot it either.
            if (Config.COMMON.lockBlockInteraction.get() || Config.COMMON.individualLockBlockInteraction.get()) {
                ItemStack blockItem = new ItemStack(state.getBlock().asItem());
                if (!blockItem.isEmpty()
                        && LockGate.isActionLockedServer(blockItem, player, "gui",
                                Config.COMMON.lockBlockInteraction, Config.COMMON.individualLockBlockInteraction)) {
                    return;
                }
            }

            // clearContent() only empties the block entity — it calls setBook(EMPTY), which touches
            // item, page and page count but never the block state. Without the reset the lectern
            // keeps HAS_BOOK and a ghost book stays on the stand. Vanilla's own menu escapes this
            // through the block entity's private container, which also calls onBookItemRemove().
            lectern.clearContent();
            LecternBlock.resetBookState(player, level, packet.pos, state, false);

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
