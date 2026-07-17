package net.bananemdnsa.historystages.client.editor.widget.popup;

import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Own popup for restricting an interactionlock entry to specific interaction actions (breed,
 * mount, trade, leash, shear, milk, name, equip, other). Toggles show the <em>blocked</em>
 * actions; all blocked is the default.
 *
 * <p>On confirm it reports the entity id and the blocked actions. An empty list means "all
 * blocked" — the default, stored as no filter.
 */
public class InteractionActionsPopup {

    private static final String[] ACTION_KEYS = EntityInteractionLockEntry.ALL_ACTIONS.toArray(new String[0]);

    private static final int PAD       = 8;
    private static final int WIDTH     = 300;
    private static final int COLS      = 2;
    private static final int HEADER_H  = 18;
    private static final int HINT_H    = 10;
    private static final int TOGGLE_H  = 14;
    private static final int TOGGLE_GAP = 2;
    private static final int FOOTER_H  = 20;

    private final BiConsumer<String, List<String>> onConfirm; // (entityId, blockedActions)

    private boolean visible = false;
    private String entityId = null;
    private List<String> current = new ArrayList<>(); // working set: actions currently BLOCKED
    private int cachedX, cachedY, cachedW, cachedH;

    public InteractionActionsPopup(BiConsumer<String, List<String>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    public boolean isVisible() { return visible; }

    public void hide() { visible = false; }

    public void show(String entityId, List<String> currentBlocked) {
        this.entityId = entityId;
        if (currentBlocked != null && !currentBlocked.isEmpty()) {
            this.current = new ArrayList<>(currentBlocked);
        } else {
            // Default = all actions blocked (matches "no filter" behaviour)
            this.current = new ArrayList<>(java.util.Arrays.asList(ACTION_KEYS));
        }
        this.visible = true;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        int rows = (ACTION_KEYS.length + COLS - 1) / COLS;
        int contentH = rows * TOGGLE_H + (rows - 1) * TOGGLE_GAP;

        int popupW = WIDTH;
        int descMaxWidth = popupW - 2 * PAD - 4;

        // Reserve enough vertical space for the longest possible description (any action).
        int maxDescLines = 1;
        for (String action : ACTION_KEYS) {
            Component sample = Component.translatable("editor.historystages.interaction_actions.action." + action)
                    .append(Component.literal(" — "))
                    .append(Component.translatable("editor.historystages.interaction_actions.desc." + action));
            int lines = font.split(sample, descMaxWidth).size();
            if (lines > maxDescLines) maxDescLines = lines;
        }
        int descBlockH = maxDescLines * (font.lineHeight + 1) + 4;

        int popupH = HEADER_H + HINT_H + 3 + contentH + descBlockH + FOOTER_H;
        int popupX = g.guiWidth() / 2 - popupW / 2;
        int popupY = g.guiHeight() / 2 - popupH / 2;

        cachedX = popupX; cachedY = popupY; cachedW = popupW; cachedH = popupH;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        g.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        g.drawCenteredString(font,
                Component.translatable("editor.historystages.interaction_actions.title"),
                popupX + popupW / 2, popupY + 5, 0xFFFFFFFF);
        int accentW = 40;
        int accentX = popupX + (popupW - accentW) / 2;
        g.fill(accentX, popupY + 15, accentX + accentW, popupY + 16, 0xFFFFCC00);

        g.drawCenteredString(font,
                Component.translatable("editor.historystages.interaction_actions.hint"),
                popupX + popupW / 2, popupY + HEADER_H, 0x888888);

        int curY = popupY + HEADER_H + HINT_H + 3;
        int toggleW = (popupW - 2 * PAD - (COLS - 1) * 3) / COLS;
        String hoveredAction = null;

        for (int j = 0; j < ACTION_KEYS.length; j++) {
            String action = ACTION_KEYS[j];
            int col = j % COLS;
            int row = j / COLS;
            int tx = popupX + PAD + col * (toggleW + 3);
            int ty = curY + row * (TOGGLE_H + TOGGLE_GAP);

            boolean blocked = current.contains(action);
            boolean hovered = mouseX >= tx && mouseX < tx + toggleW && mouseY >= ty && mouseY < ty + TOGGLE_H;
            if (hovered) hoveredAction = action;

            int bg = blocked
                    ? (hovered ? 0x40FFCC00 : 0x25FFCC00)
                    : (hovered ? 0x25FFFFFF : 0x10FFFFFF);
            g.fill(tx, ty, tx + toggleW, ty + TOGGLE_H, bg);

            int accent = blocked
                    ? (hovered ? 0xFFFFCC00 : 0xB0FFCC00)
                    : (hovered ? 0x40FFFFFF : 0x20FFFFFF);
            g.fill(tx, ty + TOGGLE_H - 1, tx + toggleW, ty + TOGGLE_H, accent);

            int textColor = blocked ? 0xFFFFFF : 0x999999;
            int dotColor  = blocked ? 0xFFFFCC00 : 0xFF555555;
            g.fill(tx + 4, ty + 6, tx + 7, ty + 9, dotColor);
            g.drawString(font,
                    Component.translatable("editor.historystages.interaction_actions.action." + action),
                    tx + 10, ty + 3, textColor, false);
        }

        int descY = popupY + popupH - FOOTER_H - descBlockH + 1;
        g.fill(popupX + PAD, descY - 1, popupX + popupW - PAD, descY, 0xFF2E2E2E);
        Component descText;
        int descColor;
        if (hoveredAction != null) {
            descText = Component.translatable("editor.historystages.interaction_actions.action." + hoveredAction)
                    .append(Component.literal(" — "))
                    .append(Component.translatable("editor.historystages.interaction_actions.desc." + hoveredAction));
            descColor = 0xCCCCCC;
        } else {
            descText = Component.translatable("editor.historystages.interaction_actions.status",
                    current.size(), ACTION_KEYS.length);
            descColor = 0x888888;
        }
        List<FormattedCharSequence> descLines = font.split(descText, descMaxWidth);
        int lineY = descY + 2;
        for (FormattedCharSequence line : descLines) {
            int lineW = font.width(line);
            g.drawString(font, line, popupX + (popupW - lineW) / 2, lineY, descColor, false);
            lineY += font.lineHeight + 1;
        }

        int btnH = 14;
        int btnY = popupY + popupH - btnH - 6;
        int qBtnW = 34;

        int allX = popupX + PAD;
        boolean allHov = mouseX >= allX && mouseX < allX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(allX, btnY, allX + qBtnW, btnY + btnH, allHov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(allX, btnY + btnH - 1, allX + qBtnW, btnY + btnH, allHov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font,
                Component.translatable("editor.historystages.lock_actions.btn_all"),
                allX + qBtnW / 2, btnY + 3, allHov ? 0xFFFFFF : 0xCCCCCC);

        int noneX = allX + qBtnW + 3;
        boolean noneHov = mouseX >= noneX && mouseX < noneX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(noneX, btnY, noneX + qBtnW, btnY + btnH, noneHov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(noneX, btnY + btnH - 1, noneX + qBtnW, btnY + btnH, noneHov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font,
                Component.translatable("editor.historystages.lock_actions.btn_none"),
                noneX + qBtnW / 2, btnY + 3, noneHov ? 0xFFFFFF : 0xCCCCCC);

        int doneW = 48;
        int doneX = popupX + popupW - doneW - PAD;
        boolean doneHov = mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(doneX, btnY, doneX + doneW, btnY + btnH, doneHov ? 0x50FFCC00 : 0x25FFCC00);
        g.fill(doneX, btnY + btnH - 1, doneX + doneW, btnY + btnH, doneHov ? 0xFFFFCC00 : 0x80FFCC00);
        g.drawCenteredString(font,
                Component.translatable("editor.historystages.lock_actions.btn_done"),
                doneX + doneW / 2, btnY + 3, doneHov ? 0xFFFFFF : 0xEEEEEE);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        int popupW = cachedW, popupH = cachedH;
        int popupX = cachedX, popupY = cachedY;
        if (popupW == 0) return true;

        int btnH = 14;
        int btnY = popupY + popupH - btnH - 6;

        int doneW = 48;
        int doneX = popupX + popupW - doneW - PAD;
        if (mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH) {
            playClick();
            confirm();
            return true;
        }

        int qBtnW = 34;
        int allX = popupX + PAD;
        if (mouseX >= allX && mouseX < allX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH) {
            playClick();
            current = new ArrayList<>(java.util.Arrays.asList(ACTION_KEYS));
            return true;
        }
        int noneX = allX + qBtnW + 3;
        if (mouseX >= noneX && mouseX < noneX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH) {
            playClick();
            current.clear();
            return true;
        }

        int curY = popupY + HEADER_H + HINT_H + 3;
        int toggleW = (popupW - 2 * PAD - (COLS - 1) * 3) / COLS;
        for (int j = 0; j < ACTION_KEYS.length; j++) {
            String action = ACTION_KEYS[j];
            int col = j % COLS;
            int row = j / COLS;
            int tx = popupX + PAD + col * (toggleW + 3);
            int ty = curY + row * (TOGGLE_H + TOGGLE_GAP);
            if (mouseX >= tx && mouseX < tx + toggleW && mouseY >= ty && mouseY < ty + TOGGLE_H) {
                playClick();
                if (current.contains(action)) current.remove(action);
                else current.add(action);
                return true;
            }
        }

        if (mouseX < popupX || mouseX > popupX + popupW || mouseY < popupY || mouseY > popupY + popupH) {
            visible = false;
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC closes without saving
            visible = false;
            return true;
        }
        return false;
    }

    private void confirm() {
        boolean allBlocked = current.size() == ACTION_KEYS.length;
        onConfirm.accept(entityId, allBlocked ? Collections.emptyList() : new ArrayList<>(current));
        visible = false;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
