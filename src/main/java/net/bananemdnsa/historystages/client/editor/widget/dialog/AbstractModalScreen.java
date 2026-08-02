package net.bananemdnsa.historystages.client.editor.widget.dialog;

import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared chrome for every modal dialog in the editor: backdrop, gold-accented frame,
 * title, optional subtitle, and the confirm/cancel button row.
 *
 * <p>Subclasses declare how tall their content is via {@link #contentHeight()} and draw
 * into the area handed to {@link #renderContent}; the box geometry is derived from that,
 * so no subclass hardcodes a box size.
 *
 * <p>{@link #renderBackground} is deliberately a no-op — 1.21 would otherwise apply the
 * menu blur shader behind the dialog. The backdrop is drawn in {@link #render} instead.
 */
public abstract class AbstractModalScreen extends Screen {

    protected static final int BACKDROP_DEFAULT = 0xC0000000;
    protected static final int FRAME_OUTER = 0xFF3D3D3D;
    protected static final int FRAME_INNER = 0xF0181818;
    protected static final int ACCENT_GOLD = 0xFFFFCC00;
    protected static final int TITLE_GOLD = 0xFFCC00;
    protected static final int SUBTITLE_GREY = 0x888888;

    /**
     * Depth the whole dialog is drawn at. Must clear the ~232 that {@code GuiGraphics.renderItem}
     * uses, or item stacks on the parent screen punch through the backdrop.
     */
    private static final int DIALOG_Z = 400;

    protected static final int PAD = 10;
    protected static final int TITLE_H = 20;
    protected static final int SUBTITLE_H = 12;
    protected static final int BUTTON_H = 20;
    protected static final int BUTTON_W = 100;
    protected static final int BUTTON_GAP = 10;

    protected final Screen parent;

    /** Box geometry, recomputed in {@link #init}. */
    protected int boxX, boxY, boxW, boxH;
    /** Top of the content area handed to {@link #renderContent}. */
    protected int contentY;

    protected AbstractModalScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    // ============ Extension points ============

    /** Height of the subclass content area, excluding title, subtitle, buttons and padding. */
    protected abstract int contentHeight();

    /** Draws the content. {@code x}/{@code y}/{@code w} bound the area reserved by {@link #contentHeight()}. */
    protected abstract void renderContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY);

    /** Invoked by the confirm button and by ENTER. Implementations close the screen themselves. */
    protected abstract void onConfirm();

    /** Backdrop fill. Override for an opaque backdrop when content would otherwise bleed through. */
    protected int backdropColor() {
        return BACKDROP_DEFAULT;
    }

    /**
     * Whether to draw the parent screen behind the backdrop, so the dialog reads as an overlay
     * on the screen it came from rather than a jump to somewhere else. Mouse coordinates are
     * passed as (-1, -1): the parent is inert while the dialog is up, and must not show hover
     * state it cannot act on.
     */
    protected boolean renderParentBehind() {
        return true;
    }

    protected int dialogWidth() {
        return 280;
    }

    /**
     * The headline drawn at the top of the dialog. Defaults to the title passed to the
     * constructor; override when a dialog changes what it is doing while open, since
     * {@code Screen.title} is final and cannot be swapped.
     */
    protected Component titleText() {
        return this.title;
    }

    /**
     * Whether the headline is centred. Override to {@code false} when the title row also
     * carries controls, so the title keeps to the left edge instead of running into them.
     */
    protected boolean titleCentered() {
        return true;
    }

    /** Optional grey line under the title. Null or empty hides it (and reclaims its height). */
    protected Component subtitle() {
        return null;
    }

    protected Component confirmLabel() {
        return Component.translatable("editor.historystages.confirm");
    }

    protected Component cancelLabel() {
        return Component.translatable("editor.historystages.cancel");
    }

    /** True when the confirm button is clickable. Override to gate on validation. */
    protected boolean canConfirm() {
        return true;
    }

    /**
     * Whether ENTER triggers confirm. True suits input dialogs, where it saves a reach for the
     * mouse. Override to false where confirming is destructive and unrecoverable — ESC followed
     * by ENTER is a common reflex, and it must not delete anything.
     */
    protected boolean confirmOnEnter() {
        return true;
    }

    protected void onCancel() {
        this.minecraft.setScreen(parent);
    }

    // ============ Layout ============

    private boolean hasSubtitle() {
        Component s = subtitle();
        return s != null && !s.getString().isEmpty();
    }

    private int subtitleHeight() {
        return hasSubtitle() ? SUBTITLE_H : 0;
    }

    @Override
    protected void init() {
        boxW = dialogWidth();
        // Padding sits above the content as well as below it and under the buttons, so the
        // content is not flush against the title.
        boxH = TITLE_H + subtitleHeight() + PAD + contentHeight() + PAD + BUTTON_H + PAD;
        boxX = (this.width - boxW) / 2;
        boxY = (this.height - boxH) / 2;
        contentY = boxY + TITLE_H + subtitleHeight() + PAD;

        buildContentWidgets();

        int btnY = boxY + boxH - PAD - BUTTON_H;
        int totalW = BUTTON_W * 2 + BUTTON_GAP;
        int confirmX = boxX + boxW / 2 - totalW / 2;
        int cancelX = confirmX + BUTTON_W + BUTTON_GAP;

        this.addRenderableWidget(StyledButton.of(confirmLabel(),
                btn -> { if (canConfirm()) onConfirm(); }, confirmX, btnY, BUTTON_W, BUTTON_H));
        this.addRenderableWidget(StyledButton.of(cancelLabel(),
                btn -> onCancel(), cancelX, btnY, BUTTON_W, BUTTON_H));
    }

    /**
     * Hook for subclasses to register their own widgets during {@link #init}, after the box
     * geometry is known but before the buttons are added (so buttons render last).
     */
    protected void buildContentWidgets() {
    }

    // ============ Rendering ============

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op — render() draws the backdrop; this avoids 1.21's menu blur shader.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (renderParentBehind() && parent != null) {
            parent.render(g, -1, -1, partialTick);
            // The parent batches its text and items and would otherwise flush them after our
            // fills, painting them back over the dialog. Force them out now.
            g.flush();
        }

        // Everything from here up is lifted above the depth the parent's items render at, so
        // the backdrop actually covers them instead of losing the depth test to them.
        g.pose().pushPose();
        g.pose().translate(0, 0, DIALOG_Z);

        g.fill(0, 0, this.width, this.height, backdropColor());

        g.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, FRAME_OUTER);
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, FRAME_INNER);
        g.fill(boxX, boxY, boxX + boxW, boxY + 2, ACCENT_GOLD);

        if (titleCentered()) {
            g.drawCenteredString(this.font, titleText(), boxX + boxW / 2, boxY + 8, TITLE_GOLD);
        } else {
            g.drawString(this.font, titleText(), boxX + PAD, boxY + 8, TITLE_GOLD, false);
        }

        if (hasSubtitle()) {
            g.drawCenteredString(this.font, subtitle(), boxX + boxW / 2, boxY + TITLE_H, SUBTITLE_GREY);
        }

        renderContent(g, boxX + PAD, contentY, boxW - PAD * 2, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        g.flush();
        g.pose().popPose();
    }

    // ============ Input ============

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // ENTER / NUMPAD ENTER
            if (!confirmOnEnter()) return true;
            if (canConfirm()) onConfirm();
            return true;
        }
        if (keyCode == 256) { // ESC
            onCancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
