package net.bananemdnsa.historystages.client.editor.widget.popup;

import net.bananemdnsa.historystages.api.editor.widget.NumberStepper;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.StructureGenerationRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Everything a structure entry's world generation can be, in one dialog: whether it is restricted
 * at all, which lock phase the limit counts in, how many instances may generate, and whether the
 * budget restarts when that phase restarts.
 *
 * <p>The old one-click "block generation" toggle is the master switch at the top. Turning it on
 * lands on {@code while locked / 0}, which is exactly what that toggle used to store, so blocking a
 * structure entirely still costs a single click. The hint line spells out what a limit of 0 means
 * in each mode, so the number never has to be decoded.
 *
 * <p>On confirm it reports the structure id and the rule, or {@code null} when the master switch is
 * off — "unlimited" is the absence of a rule, not a value.
 */
public class GenerationLimitPopup {

    private static final int ROW_H = 18;
    private static final int PAD = 10;
    private static final int WIDTH = 300;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 26;
    private static final int ROWS = 4;

    private static final int ROW_ENABLE = 0;
    private static final int ROW_MODE = 1;
    private static final int ROW_LIMIT = 2;
    private static final int ROW_RESET = 3;

    private static final int MAX_LIMIT = 999;
    private static final int BOX_S = 11;

    /** Every hint the dialog can show; the block is sized for the longest so the panel never jumps. */
    private static final String[] HINT_KEYS = {
            "editor.historystages.generation.hint.off",
            "editor.historystages.generation.hint.zero_locked",
            "editor.historystages.generation.hint.zero_unlocked",
            "editor.historystages.generation.hint.limit"
    };

    private final BiConsumer<String, StructureGenerationRule> onConfirm; // (structureId, rule or null)

    private boolean visible = false;
    private String structureId = null;

    // Working state
    private boolean enabled = false;
    private GenerationPhase phase = GenerationPhase.WHILE_LOCKED;
    private boolean resetOnRelock = false;

    /** The limit lives in the stepper; {@link #limit()} is the single reader. */
    private final NumberStepper limitStepper = new NumberStepper(0, MAX_LIMIT, 1, 0, null);
    private final EnumDropdown modeDropdown;

    private int centerX, centerY;
    private int panelX, panelY, panelW, panelH;

    public GenerationLimitPopup(BiConsumer<String, StructureGenerationRule> onConfirm) {
        this.onConfirm = onConfirm;
        this.modeDropdown = new EnumDropdown(
                List.of(GenerationPhase.WHILE_LOCKED.serialize(), GenerationPhase.AFTER_UNLOCK.serialize()),
                GenerationPhase.WHILE_LOCKED.serialize(), 0,
                raw -> Component.translatable("editor.historystages.generation.mode."
                        + GenerationPhase.parse(raw).serialize()),
                raw -> this.phase = GenerationPhase.parse(raw));
    }

    private int limit() { return limitStepper.getValue(); }

    public boolean isVisible() { return visible; }

    public void hide() { visible = false; }

    /**
     * @param current the rule stored for this entry, or null if the structure is unrestricted
     */
    public void show(String structureId, StructureGenerationRule current, int centerX, int centerY) {
        this.structureId = structureId;
        this.centerX = centerX;
        this.centerY = centerY;
        // No rule yet: start on the legacy defaults, so switching the master on reproduces the old
        // "block entirely" without touching anything else.
        this.enabled = current != null;
        this.phase = current != null ? current.phase() : GenerationPhase.WHILE_LOCKED;
        this.resetOnRelock = current != null && current.resetOnRelock();
        this.limitStepper.setValue(current != null ? current.max() : 0);
        this.modeDropdown.setValue(this.phase.serialize());
        this.modeDropdown.close();
        this.visible = true;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        int hintMaxW = WIDTH - 2 * PAD;
        int hintLines = 1;
        for (String key : HINT_KEYS) {
            int lines = font.split(Component.translatable(key), hintMaxW).size();
            if (lines > hintLines) hintLines = lines;
        }
        int hintBlockH = hintLines * (font.lineHeight + 1) + 6;

        panelW = WIDTH;
        panelH = HEADER_H + ROWS * ROW_H + hintBlockH + FOOTER_H;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(panelX + 3, panelY + 3, panelX + panelW + 3, panelY + panelH + 3, 0x50000000);
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF333333);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        g.drawCenteredString(font, Component.translatable("editor.historystages.generation.title"),
                panelX + panelW / 2, panelY + 6, 0xFFFFFFFF);
        int accentW = 40;
        int accentX = panelX + (panelW - accentW) / 2;
        g.fill(accentX, panelY + 17, accentX + accentW, panelY + 18, 0xFFFFCC00);

        renderCheckboxRow(g, font, mouseX, mouseY, ROW_ENABLE,
                "editor.historystages.generation.enable", enabled, true);
        renderModeRow(g, font, mouseX, mouseY);
        renderLimitRow(g, font, mouseX, mouseY);
        renderCheckboxRow(g, font, mouseX, mouseY, ROW_RESET,
                "editor.historystages.generation.reset", resetOnRelock, enabled);

        int hintY = rowY(ROWS) + 4;
        List<FormattedCharSequence> hintText = font.split(Component.translatable(hintKey()), hintMaxW);
        for (FormattedCharSequence line : hintText) {
            g.drawString(font, line, panelX + (panelW - font.width(line)) / 2, hintY, 0x888888, false);
            hintY += font.lineHeight + 1;
        }

        int btnH = 16;
        int btnY = panelY + panelH - btnH - 6;
        int doneW = 52;
        int doneX = panelX + panelW - doneW - PAD;
        boolean doneHov = mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(doneX, btnY, doneX + doneW, btnY + btnH, doneHov ? 0x50FFCC00 : 0x25FFCC00);
        g.fill(doneX, btnY + btnH - 1, doneX + doneW, btnY + btnH, doneHov ? 0xFFFFCC00 : 0x80FFCC00);
        g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.btn_done"),
                doneX + doneW / 2, btnY + 4, doneHov ? 0xFFFFFF : 0xEEEEEE);

        // Last, so the open list covers the rows below it instead of being covered by them.
        if (enabled) modeDropdown.renderPopup(g, font, mouseX, mouseY);
    }

    private void renderCheckboxRow(GuiGraphics g, Font font, int mouseX, int mouseY, int row,
                                   String labelKey, boolean checked, boolean active) {
        int ry = rowY(row);
        boolean hovered = active && isInRow(mouseX, mouseY, row);
        if (hovered) g.fill(panelX + PAD - 2, ry, panelX + panelW - PAD + 2, ry + ROW_H, 0x25FFFFFF);

        int boxX = panelX + PAD;
        int boxY = ry + (ROW_H - BOX_S) / 2;
        int fg = active ? 0xFFFFCC00 : 0xFF555555;
        g.fill(boxX, boxY, boxX + BOX_S, boxY + BOX_S, checked ? fg : 0xFF555555);
        g.fill(boxX + 1, boxY + 1, boxX + BOX_S - 1, boxY + BOX_S - 1, 0xFF1A1A1A);
        if (checked) g.fill(boxX + 3, boxY + 3, boxX + BOX_S - 3, boxY + BOX_S - 3, fg);

        g.drawString(font, Component.translatable(labelKey), boxX + BOX_S + 6,
                ry + (ROW_H - font.lineHeight) / 2 + 1, labelColor(active, hovered), false);
    }

    private void renderModeRow(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int ry = rowY(ROW_MODE);
        g.drawString(font, Component.translatable("editor.historystages.generation.mode"),
                panelX + PAD, ry + (ROW_H - font.lineHeight) / 2 + 1, labelColor(enabled, false), false);

        modeDropdown.setPosition(panelX + panelW - PAD - modeDropdown.getWidth(),
                ry + (ROW_H - EnumDropdown.BUTTON_HEIGHT) / 2);
        if (enabled) {
            modeDropdown.renderButton(g, font, mouseX, mouseY);
        } else {
            // The dropdown has no disabled state of its own; drawing the flat face keeps the row
            // consistent with the other greyed-out controls instead of inviting a click.
            int bx = panelX + panelW - PAD - modeDropdown.getWidth();
            int by = ry + (ROW_H - EnumDropdown.BUTTON_HEIGHT) / 2;
            g.fill(bx, by, bx + modeDropdown.getWidth(), by + EnumDropdown.BUTTON_HEIGHT, 0x08FFFFFF);
            g.fill(bx, by + EnumDropdown.BUTTON_HEIGHT - 1, bx + modeDropdown.getWidth(),
                    by + EnumDropdown.BUTTON_HEIGHT, 0x20FFFFFF);
            g.drawCenteredString(font, Component.translatable(
                            "editor.historystages.generation.mode." + phase.serialize()),
                    bx + modeDropdown.getWidth() / 2, by + 5, 0xFF555555);
        }
    }

    private void renderLimitRow(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int ry = rowY(ROW_LIMIT);
        g.drawString(font, Component.translatable("editor.historystages.generation.limit"),
                panelX + PAD, ry + (ROW_H - font.lineHeight) / 2 + 1, labelColor(enabled, false), false);

        limitStepper.setPosition(panelX + panelW - PAD - NumberStepper.width(),
                ry + (ROW_H - NumberStepper.HEIGHT) / 2);
        limitStepper.setEnabled(enabled);
        limitStepper.render(g, font, mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        // Geometry is only known once render() has run; swallow the click until then.
        if (panelW == 0) return true;

        // Before everything else: while the list is open it covers the rows underneath, so those
        // must not see the click that picks an option. Note the state is read up front — the
        // dropdown collapses itself on a click that misses it, and a click beside an open list
        // should only dismiss the list, not the whole dialog with it.
        boolean listWasOpen = enabled && modeDropdown.isExpanded();
        if (enabled && modeDropdown.mouseClicked(mouseX, mouseY)) return true;
        if (listWasOpen) return true;

        // Ahead of the rows: the stepper has to see every click, not just the ones on its own row,
        // so that a click elsewhere — Done included — commits a number being typed instead of
        // dropping it.
        if (limitStepper.mouseClicked(mouseX, mouseY)) return true;

        int btnH = 16;
        int btnY = panelY + panelH - btnH - 6;
        int doneW = 52;
        int doneX = panelX + panelW - doneW - PAD;
        if (inBox(mouseX, mouseY, doneX, btnY, doneW, btnH)) {
            playClick();
            confirm();
            return true;
        }

        if (isInRow(mouseX, mouseY, ROW_ENABLE)) {
            playClick();
            enabled = !enabled;
            // Switching off stops the list from rendering, so it must not stay logically open.
            if (!enabled) modeDropdown.close();
            return true;
        }

        // Everything below the master switch is inert while it is off.
        if (!enabled) {
            hideIfOutside(mouseX, mouseY);
            return true;
        }

        if (isInRow(mouseX, mouseY, ROW_MODE)) return true;

        if (isInRow(mouseX, mouseY, ROW_LIMIT)) return true;

        if (isInRow(mouseX, mouseY, ROW_RESET)) {
            playClick();
            resetOnRelock = !resetOnRelock;
            return true;
        }

        hideIfOutside(mouseX, mouseY);
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        // The limit field owns the keyboard while it is being typed into, ESC included.
        if (limitStepper.keyPressed(keyCode)) return true;
        if (keyCode == 256) { // ESC
            // One level at a time: an open mode list closes first, so ESC never discards the whole
            // dialog while the user was only browsing the options.
            if (modeDropdown.isExpanded()) {
                modeDropdown.close();
            } else {
                visible = false;
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        return visible && limitStepper.charTyped(c);
    }

    private void confirm() {
        limitStepper.commitEdit();
        onConfirm.accept(structureId, enabled
                ? new StructureGenerationRule(structureId, phase, limit(), resetOnRelock)
                : null);
        visible = false;
    }

    private void hideIfOutside(double mouseX, double mouseY) {
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            visible = false;
        }
    }

    private String hintKey() {
        if (!enabled) return "editor.historystages.generation.hint.off";
        if (limit() > 0) return "editor.historystages.generation.hint.limit";
        return phase == GenerationPhase.WHILE_LOCKED
                ? "editor.historystages.generation.hint.zero_locked"
                : "editor.historystages.generation.hint.zero_unlocked";
    }

    private int rowY(int row) {
        return panelY + HEADER_H + row * ROW_H;
    }

    private boolean isInRow(double mouseX, double mouseY, int row) {
        int ry = rowY(row);
        return mouseX >= panelX + PAD - 2 && mouseX < panelX + panelW - PAD + 2
                && mouseY >= ry && mouseY < ry + ROW_H;
    }


    private static boolean inBox(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int labelColor(boolean active, boolean hovered) {
        if (!active) return 0xFF555555;
        return hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
