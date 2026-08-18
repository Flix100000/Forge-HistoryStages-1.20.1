package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.network.OpenLecternScrollPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.util.ScrollVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Opens the open scroll document when a lectern holding one is right-clicked.
 *
 * <p>The lectern's own menu is no use to us: {@code LecternBlockEntity.hasBook()} hard-checks for
 * written and writable books, so it reports false for our scroll and the container invalidates
 * itself the moment it opens. The block entity also never syncs its item, so the reader's client
 * cannot learn the stage on its own — the server has to say it.
 *
 * <p>Server-side only, for that same reason: a client sees an empty {@code getBook()} and could
 * never recognise the case. Nothing is lost by it. {@code ServerPlayerGameMode.useItemOn} sends
 * the interaction packet regardless of what the local prediction returns, so the server always
 * sees the click, and {@code LecternBlock.use} guards {@code openScreen} behind
 * {@code !level.isClientSide()} — the client's prediction is the same arm swing any lectern gives.
 *
 * <p>Two further consequences of that {@code hasBook()} check are harmless but surprising: a
 * comparator on a scroll lectern reads a constant 14, because the page count stays 0, and
 * {@code page} loads back as -1 after a world restart ({@code Mth.clamp(0, 0, -1)}). Only the 14
 * is ever read by this mod, and the page is never read at all.
 */
@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LecternScrollHandler {

    private LecternScrollHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof LecternBlock)) return;
        // Empty lectern: let vanilla run, so the lectern_books tag places the scroll as usual.
        if (!state.getValue(LecternBlock.HAS_BOOK)) return;
        if (!(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) return;

        ItemStack book = lectern.getBook();
        // A real book still opens the vanilla menu.
        if (!book.is(ModItems.RESEARCH_SCROLL_OPEN.get())) return;

        // Mirror vanilla's whole gate, not just half of it. ServerPlayerGameMode runs the block
        // branch only when the Result allows it AND the player is not sneak-placing; this mod's
        // own BlockLockHandler denies a locked block's GUI through exactly that Result, so
        // reading only the sneak half would open the document on a lectern the player may not
        // touch. LOW priority makes sure that handler has already written it.
        boolean holdingSomething = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        boolean bothHandsBypassSneak = player.getMainHandItem().doesSneakBypassUse(level, pos, player)
                && player.getOffhandItem().doesSneakBypassUse(level, pos, player);
        boolean sneakPlacing = player.isSecondaryUseActive() && holdingSomething && !bothHandsBypassSneak;
        if (!(event.getUseBlock() == Event.Result.ALLOW
                || (event.getUseBlock() == Event.Result.DEFAULT && !sneakPlacing))) return;

        // Without this, the vanilla use() reaches openScreen and the useless vanilla menu opens
        // behind our document.
        event.setCanceled(true);
        player.awardStat(Stats.INTERACT_WITH_LECTERN);

        String stageId = ScrollVariants.readStageResearch(book);
        // An untagged scroll still opens: the screen says the stage is unknown, which beats a
        // lectern that silently does nothing when you click it.
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenLecternScrollPacket(stageId == null ? "" : stageId, pos));
    }
}
