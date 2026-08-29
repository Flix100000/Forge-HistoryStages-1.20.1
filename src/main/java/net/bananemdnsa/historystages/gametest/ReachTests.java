package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.network.PacketReach;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What {@link PacketReach} refuses, with a real player in a real level.
 *
 * <p>Four packets carry a {@code BlockPos} the client chose. Two of them used to hand that
 * position straight to {@code getBlockEntity}, which loads the chunk if it is not there — so a
 * client picking its own coordinates could make the server generate world on demand. The guard is
 * three lines, which is exactly the kind of thing that gets dropped again by whoever writes the
 * fifth such packet.
 *
 * <p>The last test is the one that matters: it asks for a block entity far away and then checks
 * that the chunk is <em>still</em> not loaded. Remove the guard and that assertion fails, because
 * asking is what loads it.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReachTests {

    private ReachTests() {}

    /** Far enough to be outside anything a test world keeps loaded, well inside the world border. */
    private static final int FAR_AWAY = 100_000;

    @GameTest(template = "empty")
    public static void theBlockUnderThePlayerIsInReach(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ServerPlayer player = playerAt(helper, origin);

        if (!PacketReach.canUse(player, origin)) {
            helper.fail("a player standing on a block was refused it; the guard rejects "
                    + "legitimate use, which would break every pedestal and lectern");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBlockThirtyAwayIsRefused(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ServerPlayer player = playerAt(helper, origin);

        // Straight up, so the position is in the player's own chunk and certainly loaded. That
        // isolates the distance rule: the loaded check cannot be what refuses this one.
        BlockPos high = origin.above(30);

        ServerLevel level = helper.getLevel();
        if (!level.isLoaded(high)) {
            helper.fail("precondition failed: " + high + " is not loaded, so this test would "
                    + "pass for the wrong reason - it is meant to exercise the distance rule");
            return;
        }
        if (PacketReach.canUse(player, high)) {
            helper.fail("a block 30 away was accepted; the distance rule is not applied");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anUnloadedPositionIsRefusedWithoutLoadingIt(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ServerPlayer player = playerAt(helper, origin);
        ServerLevel level = helper.getLevel();

        BlockPos far = origin.offset(FAR_AWAY, 0, FAR_AWAY);
        if (level.isLoaded(far)) {
            helper.fail("precondition failed: " + far + " is already loaded, so nothing here "
                    + "can show whether asking for it would have loaded it");
            return;
        }

        if (PacketReach.blockEntityInReach(player, far) != null) {
            helper.fail("a block entity came back from " + far + ", which no player can reach");
            return;
        }

        // The whole point. Level.getBlockEntity runs getChunkAt -> getChunk(FULL) ->
        // ServerChunkCache.getChunk(requireChunk = true) -> managedBlock, so an unguarded call
        // blocks the server thread and generates the chunk. If this assertion ever fails, the
        // guard is gone and a client can drive world generation by sending coordinates.
        if (level.isLoaded(far)) {
            helper.fail("asking for a block entity at " + far + " loaded the chunk; the "
                    + "position reached the world instead of being refused first");
            return;
        }
        helper.succeed();
    }

    /** A player standing at {@code pos}, so distances in these tests are measured from a known point. */
    private static ServerPlayer playerAt(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = GameTestPlayers.create(helper);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }
}
