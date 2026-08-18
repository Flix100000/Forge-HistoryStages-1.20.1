package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractModalScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * A modal confirmation dialog overlay: a message and a confirm/cancel pair.
 */
public class ConfirmDialog extends AbstractModalScreen {

    private static final int MESSAGE_GREY = 0xAAAAAA;
    private static final int LINE_H = 10;

    private final Component message;
    /** Named apart from the onConfirm() hook it is invoked from, which would shadow confusingly. */
    private final Runnable confirmAction;

    /**
     * Computed in {@link #contentHeight()} (which {@code AbstractModalScreen.init()} always
     * calls before the first render) and reused by {@link #renderContent}, so the wrap only
     * runs once per open rather than every frame.
     */
    private List<FormattedCharSequence> wrappedMessage = List.of();

    public ConfirmDialog(Screen parent, Component title, Component message, Runnable onConfirm) {
        super(parent, title);
        this.message = message;
        this.confirmAction = onConfirm;
    }

    /**
     * Confirmation here means deleting a stage or discarding unsaved edits, so it stays a
     * deliberate click. The pre-refactor dialog had no ENTER handling either.
     */
    @Override
    protected boolean confirmOnEnter() {
        return false;
    }

    @Override
    protected int dialogWidth() {
        return 250;
    }

    /**
     * Wraps to the dialog's content width and grows to fit. Every message used before this
     * wrapping was added stayed within one line, so {@code wrappedMessage.size()} is 1 for them
     * and this still returns the original fixed 24 — existing dialogs render pixel-identical.
     */
    @Override
    protected int contentHeight() {
        int width = dialogWidth() - PAD * 2;
        wrappedMessage = this.font.split(message, width);
        return Math.max(24, wrappedMessage.size() * LINE_H + 8);
    }

    @Override
    protected void renderContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int cy = y + 8;
        for (FormattedCharSequence line : wrappedMessage) {
            g.drawCenteredString(this.font, line, x + w / 2, cy, MESSAGE_GREY);
            cy += LINE_H;
        }
    }

    @Override
    protected void onConfirm() {
        confirmAction.run();
    }
}
