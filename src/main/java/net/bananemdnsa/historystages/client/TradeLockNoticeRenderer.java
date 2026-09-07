package net.bananemdnsa.historystages.client;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.TradeLockKind;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Says, inside the trade window, why there is nothing in it.
 *
 * <p>A merchant whose offers were all held back looks exactly like a merchant who has none:
 * an empty list and no reason given. Every other lock in this mod has a surface a player can
 * read — a message, a crossed-out slot, a red overlay — and this was the one that had only an
 * actionbar line, three lines below where the player was actually looking.
 *
 * <p>Drawn over the finished screen rather than woven into it, so vanilla's own window is
 * untouched and any other mod that decorates the same screen keeps working. Nothing is drawn
 * unless the server said this particular window is empty on our account, which means a merchant
 * that genuinely has no trades still gets no explanation from us — there is nothing to explain.
 *
 * <p>Over the whole screen rather than through the container screen's own foreground hook, which
 * would be the tidier place. That hook draws in the window's local coordinates, and getting the
 * space wrong puts the text in the corner of the screen instead of in the window — a mistake only
 * a running client can catch. Here the numbers are absolute and can be reasoned about. The cost is
 * that this draws after tooltips, so a tooltip from the player's own inventory reaching up past
 * the offer area would be covered for as long as it is up. Worth revisiting if it ever shows.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class TradeLockNoticeRenderer {

    /**
     * The offer column, measured from the window's left edge.
     *
     * <p>Vanilla lists the offers down the left of a 276-wide window and keeps the right for the
     * trading slots. The notice belongs in the column: the middle of the window is the slots, and
     * a panel across those says something is broken rather than that something is missing.
     */
    private static final int OFFER_AREA_LEFT = 5;

    /** Right edge of the column, short of the scrollbar vanilla puts beside it. */
    private static final int OFFER_AREA_RIGHT = 94;

    /** Height of the merchant's title row, which the notice must stay clear of. */
    private static final int TITLE_HEIGHT = 16;

    /**
     * Bottom of the offer area, measured from the window's top.
     *
     * <p>Vanilla's merchant window is 166 tall and the player's inventory starts at 107. The
     * notice belongs above that: writing across somebody's own inventory would look like a bug
     * rather than a message.
     */
    private static final int OFFER_AREA_BOTTOM = 107;

    private static final int LINE_HEIGHT = 10;

    /**
     * Faint rather than solid. The offer column is light enough to read dark text on, so the
     * panel is there for the resource pack that swaps the texture for a dark one — not to be
     * seen in its own right.
     */
    private static final int BACKDROP = 0x66101010;
    private static final int BACKDROP_PAD = 3;

    /** Drawn size of the lock. Larger than the 8x8 on an item slot: nothing here sets a size. */
    private static final int LOCK_SIZE = 16;

    /** Space between the lock and the first line of text. */
    private static final int LOCK_GAP = 3;

    private TradeLockNoticeRenderer() {
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MerchantScreen screen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!TradeLockNotice.appliesTo(mc.player.containerMenu.containerId)) return;
        // The server sends the notice only for a window it emptied, but the player can still be
        // shown offers a moment later by any mod that pushes its own list — in which case there
        // is a list on screen and the notice would be a lie.
        if (!screen.getMenu().getOffers().isEmpty()) return;

        int openId = mc.player.containerMenu.containerId;
        draw(event.getGuiGraphics(), mc.font, screen.getGuiLeft(), screen.getGuiTop(),
                TradeLockNotice.stageNamesFor(openId), TradeLockNotice.kindFor(openId));
    }

    /**
     * Draws the notice in the offer column, where the offers would have been.
     *
     * <p>Takes plain numbers rather than the screen, so everything about where the text goes is
     * arithmetic in one place — the caller has already decided which screen and which window.
     */
    private static void draw(GuiGraphics g, Font font, int guiLeft, int guiTop,
                             List<String> stageNames, TradeLockKind kind) {
        int available = OFFER_AREA_RIGHT - OFFER_AREA_LEFT - 4;
        List<FormattedCharSequence> lines = new ArrayList<>(
                font.split(LockMessages.tradeLocked().withStyle(ChatFormatting.RED), available));

        // Off by default: a merchant with nothing to say is a puzzle some packs want to keep.
        if (Config.VISUAL.tradeShowStagesInWindow.get() && !stageNames.isEmpty()) {
            for (String name : stageNames) {
                MutableComponent required =
                        Component.translatable("message.historystages.locked_stage", name);
                lines.addAll(font.split(required, available));
            }
        }
        if (lines.isEmpty()) return;

        int blockHeight = LOCK_SIZE + LOCK_GAP + lines.size() * LINE_HEIGHT;
        int centreX = guiLeft + (OFFER_AREA_LEFT + OFFER_AREA_RIGHT) / 2;
        int top = guiTop + TITLE_HEIGHT + (OFFER_AREA_BOTTOM - TITLE_HEIGHT - blockHeight) / 2;

        int widest = LOCK_SIZE;
        for (FormattedCharSequence line : lines) widest = Math.max(widest, font.width(line));

        g.fill(centreX - widest / 2 - BACKDROP_PAD, top - BACKDROP_PAD,
                centreX + widest / 2 + BACKDROP_PAD, top + blockHeight + BACKDROP_PAD - 2,
                BACKDROP);

        LockIconRenderer.drawSized(g, iconFor(kind), centreX - LOCK_SIZE / 2, top, LOCK_SIZE);

        int y = top + LOCK_SIZE + LOCK_GAP;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, centreX - font.width(line) / 2, y, 0xFFFFFF, false);
            y += LINE_HEIGHT;
        }
    }

    /**
     * The lock that matches what emptied this window.
     *
     * <p>The two icon switches in the config are not consulted. They govern the overlay drawn on
     * item slots in an inventory; applied here they would make the notice appear with and without
     * its symbol for no reason a player could follow, and the silver-lock switch would draw an
     * individual lock as a global one — a wrong answer where none had been asked for.
     */
    private static ResourceLocation iconFor(TradeLockKind kind) {
        return switch (kind) {
            case INDIVIDUAL -> LockIconRenderer.individualIcon();
            case DUAL -> LockIconRenderer.dualIcon();
            case GLOBAL -> LockIconRenderer.globalIcon();
        };
    }
}
