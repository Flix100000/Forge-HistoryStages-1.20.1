package net.bananemdnsa.historystages.client.editor.widget.popup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.bananemdnsa.historystages.data.TradeProfessionEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Restricts one gated profession to some of the merchant's five levels.
 *
 * <p>Answers the question a profession list on its own cannot: "librarians, but only once they
 * are past apprentice." Without it the only way to say that is to gate level 4 and 5 outright,
 * which takes every other profession's experts with it.
 *
 * <p>The switches show the levels that <strong>are</strong> gated, and all five is the default —
 * that is what a bare profession has always meant. Turning all five on again is therefore the
 * same as no narrowing at all, and it is stored as none.
 *
 * <p>Deliberately simpler than the spawn-source and interaction-action popups it sits beside: one
 * column, five rows, no per-row description. Those two have nine keys apiece whose names need
 * explaining; "Novice" through "Master" explain themselves, and a description strip under five
 * self-evident rows would be furniture.
 */
public class TradeLevelsPopup {

    private static final String[] LEVEL_KEYS =
            TradeProfessionEntry.ALL_LEVELS.toArray(new String[0]);

    private static final int PAD = 8;
    private static final int WIDTH = 210;
    private static final int HEADER_H = 18;
    private static final int HINT_H = 10;
    private static final int ROW_H = 14;
    private static final int ROW_GAP = 2;
    private static final int FOOTER_H = 20;
    private static final int BTN_H = 14;

    /** Reports the profession and the levels it gates; an empty list means every level. */
    private final BiConsumer<String, List<String>> onConfirm;

    private boolean visible = false;
    private String professionId = null;
    private List<String> current = new ArrayList<>();
    private int cachedX, cachedY, cachedW, cachedH;

    public TradeLevelsPopup(BiConsumer<String, List<String>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    public boolean isVisible() {
        return visible;
    }

    public void hide() {
        visible = false;
    }

    public void show(String professionId, List<String> currentlyGated) {
        this.professionId = professionId;
        this.current = (currentlyGated != null && !currentlyGated.isEmpty())
                ? new ArrayList<>(currentlyGated)
                // No narrowing means every level, which is what the switches then show.
                : new ArrayList<>(TradeProfessionEntry.ALL_LEVELS);
        this.visible = true;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        int contentH = LEVEL_KEYS.length * ROW_H + (LEVEL_KEYS.length - 1) * ROW_GAP;
        int popupW = WIDTH;
        int popupH = HEADER_H + HINT_H + 3 + contentH + FOOTER_H;
        int popupX = g.guiWidth() / 2 - popupW / 2;
        int popupY = g.guiHeight() / 2 - popupH / 2;

        cachedX = popupX;
        cachedY = popupY;
        cachedW = popupW;
        cachedH = popupH;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        g.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        g.drawCenteredString(font, Component.translatable("editor.historystages.trade_levels.title"),
                popupX + popupW / 2, popupY + 5, 0xFFFFFFFF);
        int accentW = 40;
        g.fill(popupX + (popupW - accentW) / 2, popupY + 15,
                popupX + (popupW + accentW) / 2, popupY + 16, 0xFFFFCC00);

        g.drawCenteredString(font, Component.translatable("editor.historystages.trade_levels.hint"),
                popupX + popupW / 2, popupY + HEADER_H, 0x888888);

        int rowW = popupW - 2 * PAD;
        int y = popupY + HEADER_H + HINT_H + 3;
        for (String level : LEVEL_KEYS) {
            int x = popupX + PAD;
            boolean gated = current.contains(level);
            boolean hovered = mouseX >= x && mouseX < x + rowW && mouseY >= y && mouseY < y + ROW_H;

            g.fill(x, y, x + rowW, y + ROW_H, gated
                    ? (hovered ? 0x40FFCC00 : 0x25FFCC00)
                    : (hovered ? 0x25FFFFFF : 0x10FFFFFF));
            g.fill(x, y + ROW_H - 1, x + rowW, y + ROW_H, gated
                    ? (hovered ? 0xFFFFCC00 : 0xB0FFCC00)
                    : (hovered ? 0x40FFFFFF : 0x20FFFFFF));
            g.fill(x + 4, y + 6, x + 7, y + 9, gated ? 0xFFFFCC00 : 0xFF555555);
            g.drawString(font, Component.translatable("merchant.level." + level).getString()
                            + " §8(" + level + ")",
                    x + 10, y + 3, gated ? 0xFFFFFF : 0x999999, false);
            y += ROW_H + ROW_GAP;
        }

        int btnY = popupY + popupH - BTN_H - 6;
        drawButton(g, font, popupX + PAD, btnY, 34, mouseX, mouseY,
                "editor.historystages.lock_actions.btn_all", false);
        drawButton(g, font, popupX + PAD + 37, btnY, 34, mouseX, mouseY,
                "editor.historystages.lock_actions.btn_none", false);
        drawButton(g, font, popupX + popupW - 48 - PAD, btnY, 48, mouseX, mouseY,
                "editor.historystages.lock_actions.btn_done", true);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        if (cachedW == 0) return true;

        int btnY = cachedY + cachedH - BTN_H - 6;
        if (inButton(mouseX, mouseY, cachedX + cachedW - 48 - PAD, btnY, 48)) {
            playClick();
            confirm();
            return true;
        }
        if (inButton(mouseX, mouseY, cachedX + PAD, btnY, 34)) {
            playClick();
            current = new ArrayList<>(TradeProfessionEntry.ALL_LEVELS);
            return true;
        }
        if (inButton(mouseX, mouseY, cachedX + PAD + 37, btnY, 34)) {
            playClick();
            current.clear();
            return true;
        }

        int rowW = cachedW - 2 * PAD;
        int y = cachedY + HEADER_H + HINT_H + 3;
        for (String level : LEVEL_KEYS) {
            if (mouseX >= cachedX + PAD && mouseX < cachedX + PAD + rowW
                    && mouseY >= y && mouseY < y + ROW_H) {
                playClick();
                if (!current.remove(level)) current.add(level);
                return true;
            }
            y += ROW_H + ROW_GAP;
        }

        if (mouseX < cachedX || mouseX > cachedX + cachedW
                || mouseY < cachedY || mouseY > cachedY + cachedH) {
            visible = false;
        }
        return true;
    }

    /** ESC closes without saving, the same as the two popups beside it. */
    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == 256) {
            visible = false;
            return true;
        }
        return false;
    }

    /**
     * Reports the choice.
     *
     * <p>Every level and no level both report an empty list, and both mean "no narrowing". An
     * entry gating nothing at all is not a state worth having: it would sit in the list looking
     * like a lock and do nothing, and nobody would be able to tell why.
     */
    private void confirm() {
        boolean everyLevel = current.size() >= LEVEL_KEYS.length;
        onConfirm.accept(professionId,
                everyLevel || current.isEmpty() ? List.of() : new ArrayList<>(current));
        visible = false;
    }

    private void drawButton(GuiGraphics g, Font font, int x, int y, int w,
                            int mouseX, int mouseY, String langKey, boolean primary) {
        boolean hovered = inButton(mouseX, mouseY, x, y, w);
        g.fill(x, y, x + w, y + BTN_H, primary
                ? (hovered ? 0x50FFCC00 : 0x25FFCC00)
                : (hovered ? 0x25FFFFFF : 0x10FFFFFF));
        g.fill(x, y + BTN_H - 1, x + w, y + BTN_H, primary
                ? (hovered ? 0xFFFFCC00 : 0x80FFCC00)
                : (hovered ? 0x80FFFFFF : 0x40FFFFFF));
        g.drawCenteredString(font, Component.translatable(langKey), x + w / 2, y + 3,
                hovered ? 0xFFFFFF : (primary ? 0xEEEEEE : 0xCCCCCC));
    }

    private boolean inButton(double mouseX, double mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BTN_H;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
