package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractModalScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A modal confirmation dialog overlay: a message and a confirm/cancel pair.
 */
public class ConfirmDialog extends AbstractModalScreen {

    private static final int MESSAGE_GREY = 0xAAAAAA;

    private final Component message;
    /** Named apart from the onConfirm() hook it is invoked from, which would shadow confusingly. */
    private final Runnable confirmAction;

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

    @Override
    protected int contentHeight() {
        return 24;
    }

    @Override
    protected void renderContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        g.drawCenteredString(this.font, message, x + w / 2, y + 8, MESSAGE_GREY);
    }

    @Override
    protected void onConfirm() {
        confirmAction.run();
    }
}
