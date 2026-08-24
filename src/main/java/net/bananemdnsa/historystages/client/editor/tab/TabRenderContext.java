package net.bananemdnsa.historystages.client.editor.tab;

import java.util.function.BiConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Everything a tab needs in order to draw its own content without knowing which screen is hosting
 * it.
 *
 * <p>The rectangle already has the scroll applied, so a tab draws at {@link #y()} and never asks
 * how far the host has scrolled. {@link #clipTop()} and {@link #clipBottom()} are the host's
 * scissor bounds, for a tab that opens a scissor of its own and has to stay inside them.
 *
 * @param graphics     the frame being drawn into
 * @param font         the host's font — a tab must not reach for {@code Minecraft.getInstance()}
 * @param x            left edge of the content area
 * @param y            top edge of the first row, scroll already applied
 * @param width        width of the content area
 * @param clipTop      top of the host's scissor
 * @param clipBottom   bottom of the host's scissor
 * @param mouseX       cursor position
 * @param mouseY       cursor position
 * @param inputBlocked true while an overlay or a context menu is up. A tab must not report hover
 *                     while this is set, or rows light up under an open popup
 * @param tooltipSink  where {@link #tooltip} delivers to. Not called directly — see the note there
 */
public record TabRenderContext(GuiGraphics graphics, Font font, int x, int y, int width,
                               int clipTop, int clipBottom, int mouseX, int mouseY,
                               boolean inputBlocked, BiConsumer<String, String> tooltipSink) {

    /**
     * Asks the host to show a tooltip this frame.
     *
     * <p>Call this, not {@code tooltipSink()}. A record accessor named like an action returns the
     * handler instead of running it, which compiles, throws nothing and does nothing — that
     * mistake once disabled this editor's whole add menu.
     *
     * @param key  identifies what is hovered, so the host can restart its delay when it changes
     * @param text the resolved text — a tab translates its own lang keys
     */
    public void tooltip(String key, String text) {
        tooltipSink.accept(key, text);
    }
}
