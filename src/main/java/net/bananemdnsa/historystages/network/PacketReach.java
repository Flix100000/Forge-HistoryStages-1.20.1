package net.bananemdnsa.historystages.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The gate every packet carrying a client-chosen {@link BlockPos} has to pass before that
 * position reaches the world.
 *
 * <p>This is not politeness about reach. {@code Level.getBlockEntity} runs
 * {@code getChunkAt} → {@code getChunk(x, z, FULL)} → {@code ServerChunkCache.getChunk(…,
 * requireChunk = true)} → {@code managedBlock(…)}: on a position whose chunk is not loaded,
 * the <em>server thread blocks and generates it</em>. A client that picks its own coordinates
 * therefore holds a lever on world generation, and spamming far-away positions is enough to
 * stall the tick loop. The loaded check is what takes that lever away; the distance check is
 * what keeps a player from operating a block they are nowhere near.
 *
 * <p>The distance is {@code 64.0} squared — eight blocks, the same figure vanilla uses in
 * {@code Container.stillValid} — so a player who can legitimately have the GUI open passes and
 * nothing else does.
 */
public final class PacketReach {

    /** Squared distance a player may be from a block they operate. Matches vanilla containers. */
    private static final double MAX_DISTANCE_SQR = 64.0;

    private PacketReach() {}

    /**
     * True when {@code player} could plausibly be using the block at {@code pos}: its chunk is
     * already loaded, and the player stands within eight blocks of the block's centre.
     */
    public static boolean canUse(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos)) return false;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= MAX_DISTANCE_SQR;
    }

    /**
     * The block entity at {@code pos}, or null when {@link #canUse} refuses the position.
     *
     * <p>For handlers that read a block entity: it keeps the {@code getBlockEntity} call — and
     * with it the chunk load described above — behind the check, rather than beside it.
     */
    @Nullable
    public static BlockEntity blockEntityInReach(ServerPlayer player, BlockPos pos) {
        if (!canUse(player, pos)) return null;
        return player.level().getBlockEntity(pos);
    }
}
