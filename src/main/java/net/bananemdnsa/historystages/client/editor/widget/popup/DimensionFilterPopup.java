package net.bananemdnsa.historystages.client.editor.widget.popup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Own popup (separate from the spawn-sources popup) for restricting a spawnlock entry to
 * specific dimensions. Shows a scrollable checklist of all known dimensions; checked = blocked
 * (default for every dimension). Unchecking a dimension allows the entity to spawn there.
 *
 * <p>On confirm it reports the entity id and the list of <em>allowed</em> (unchecked) dimensions.
 * An empty list means "no filter" — blocked in all dimensions (the default).
 */
public class DimensionFilterPopup {

    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = 9;
    private static final int PAD = 10;
    private static final int WIDTH = 340;
    private static final int HEADER_H = 22;
    private static final int HINT_H = 13;
    private static final int FOOTER_H = 26;

    private final BiConsumer<String, List<String>> onConfirm; // (entityId, allowedDimensions)

    private boolean visible = false;
    private String entityId = null;
    private List<String> allDims = new ArrayList<>();   // all known dimension ids (sorted)
    private List<String> blocked = new ArrayList<>();   // working set: dims currently BLOCKED (checked)
    private int scroll = 0;
    private int panelX, panelY, panelW, panelH;

    public DimensionFilterPopup(BiConsumer<String, List<String>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    public boolean isVisible() { return visible; }

    public void hide() { visible = false; }

    public void show(String entityId, List<String> currentAllowed, int centerX, int centerY) {
        this.entityId = entityId;
        this.scroll = 0;
        this.allDims = loadKnownDimensions(currentAllowed);
        // Working set = blocked dims = all known dims minus the allowed ones stored on the entry.
        this.blocked = new ArrayList<>();
        for (String dim : allDims) {
            if (currentAllowed == null || !currentAllowed.contains(dim)) blocked.add(dim);
        }

        int visibleRows = Math.min(VISIBLE_ROWS, Math.max(1, allDims.size()));
        panelW = WIDTH;
        panelH = HEADER_H + HINT_H + 3 + visibleRows * ROW_H + FOOTER_H;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;
        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        this.visible = true;
    }

    private List<String> loadKnownDimensions(List<String> alsoInclude) {
        List<String> dims = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (ResourceKey<Level> level : mc.getConnection().levels()) {
                dims.add(level.location().toString());
            }
        }
        if (!dims.contains("minecraft:overworld")) dims.add("minecraft:overworld");
        if (!dims.contains("minecraft:the_nether")) dims.add("minecraft:the_nether");
        if (!dims.contains("minecraft:the_end")) dims.add("minecraft:the_end");
        // Also include any dimension already stored on the entry (in case the server isn't reporting it now).
        if (alsoInclude != null) for (String d : alsoInclude) if (!dims.contains(d)) dims.add(d);
        dims.sort(String::compareToIgnoreCase);
        return dims;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        int visibleRows = Math.min(VISIBLE_ROWS, Math.max(1, allDims.size()));

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(panelX + 3, panelY + 3, panelX + panelW + 3, panelY + panelH + 3, 0x50000000);
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF333333);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        g.drawCenteredString(font, Component.translatable("editor.historystages.dimension_filter.title"),
                panelX + panelW / 2, panelY + 6, 0xFFFFFFFF);
        int accentW = 40;
        int accentX = panelX + (panelW - accentW) / 2;
        g.fill(accentX, panelY + 17, accentX + accentW, panelY + 18, 0xFFFFCC00);

        g.drawCenteredString(font, Component.translatable("editor.historystages.dimension_filter.hint"),
                panelX + panelW / 2, panelY + HEADER_H, 0x888888);

        int listY = panelY + HEADER_H + HINT_H + 3;
        int maxScroll = Math.max(0, allDims.size() - visibleRows);
        if (scroll > maxScroll) scroll = maxScroll;
        for (int row = 0; row < visibleRows; row++) {
            int idx = scroll + row;
            if (idx >= allDims.size()) break;
            String dim = allDims.get(idx);
            int ry = listY + row * ROW_H;
            boolean hovered = mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
                    && mouseY >= ry && mouseY < ry + ROW_H;
            if (hovered) g.fill(panelX + PAD - 2, ry, panelX + panelW - PAD + 2, ry + ROW_H, 0x25FFFFFF);
            boolean blk = blocked.contains(dim);
            // Draw a checkbox: outlined box, filled when blocked (checked).
            int boxS = 11;
            int boxX = panelX + PAD;
            int boxY = ry + (ROW_H - boxS) / 2;
            g.fill(boxX, boxY, boxX + boxS, boxY + boxS, blk ? 0xFFFFCC00 : 0xFF555555);
            g.fill(boxX + 1, boxY + 1, boxX + boxS - 1, boxY + boxS - 1, 0xFF1A1A1A);
            if (blk) g.fill(boxX + 3, boxY + 3, boxX + boxS - 3, boxY + boxS - 3, 0xFFFFCC00);
            g.drawString(font, dim, boxX + boxS + 6, ry + (ROW_H - font.lineHeight) / 2 + 1,
                    blk ? 0xFFFFFFFF : 0xFF888888, false);
        }

        // Scrollbar indicator
        if (maxScroll > 0) {
            int barX = panelX + panelW - 4;
            int barTop = listY;
            int barH = visibleRows * ROW_H;
            g.fill(barX, barTop, barX + 3, barTop + barH, 0xFF252525);
            int thumbH = Math.max(10, (int) ((float) visibleRows / allDims.size() * barH));
            int thumbY = barTop + (int) ((float) scroll / maxScroll * (barH - thumbH));
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFF888888);
        }

        int btnH = 16;
        int btnY = panelY + panelH - btnH - 6;
        int qBtnW = 44;
        int allX = panelX + PAD;
        boolean allHov = mouseX >= allX && mouseX < allX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(allX, btnY, allX + qBtnW, btnY + btnH, allHov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(allX, btnY + btnH - 1, allX + qBtnW, btnY + btnH, allHov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.btn_all"),
                allX + qBtnW / 2, btnY + 4, allHov ? 0xFFFFFF : 0xCCCCCC);
        int noneX = allX + qBtnW + 3;
        boolean noneHov = mouseX >= noneX && mouseX < noneX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(noneX, btnY, noneX + qBtnW, btnY + btnH, noneHov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(noneX, btnY + btnH - 1, noneX + qBtnW, btnY + btnH, noneHov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.btn_none"),
                noneX + qBtnW / 2, btnY + 4, noneHov ? 0xFFFFFF : 0xCCCCCC);
        int doneW = 52;
        int doneX = panelX + panelW - doneW - PAD;
        boolean doneHov = mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(doneX, btnY, doneX + doneW, btnY + btnH, doneHov ? 0x50FFCC00 : 0x25FFCC00);
        g.fill(doneX, btnY + btnH - 1, doneX + doneW, btnY + btnH, doneHov ? 0xFFFFCC00 : 0x80FFCC00);
        g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.btn_done"),
                doneX + doneW / 2, btnY + 4, doneHov ? 0xFFFFFF : 0xEEEEEE);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;

        int btnH = 16;
        int btnY = panelY + panelH - btnH - 6;
        int qBtnW = 44;
        int allX = panelX + PAD;
        int noneX = allX + qBtnW + 3;
        int doneW = 52;
        int doneX = panelX + panelW - doneW - PAD;

        if (mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH) {
            playClick();
            confirm();
            return true;
        }
        if (mouseX >= allX && mouseX < allX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH) {
            playClick();
            blocked = new ArrayList<>(allDims); // all blocked
            return true;
        }
        if (mouseX >= noneX && mouseX < noneX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH) {
            playClick();
            blocked.clear();
            return true;
        }

        int visibleRows = Math.min(VISIBLE_ROWS, Math.max(1, allDims.size()));
        int listY = panelY + HEADER_H + HINT_H + 3;
        for (int row = 0; row < visibleRows; row++) {
            int idx = scroll + row;
            if (idx >= allDims.size()) break;
            int ry = listY + row * ROW_H;
            if (mouseX >= panelX + PAD - 2 && mouseX < panelX + panelW - PAD + 2
                    && mouseY >= ry && mouseY < ry + ROW_H) {
                playClick();
                String dim = allDims.get(idx);
                if (blocked.contains(dim)) blocked.remove(dim);
                else blocked.add(dim);
                return true;
            }
        }

        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            visible = false;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!visible) return false;
        int visibleRows = Math.min(VISIBLE_ROWS, Math.max(1, allDims.size()));
        int maxScroll = Math.max(0, allDims.size() - visibleRows);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
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
        // Allowed = known dims that are NOT blocked (unchecked).
        List<String> allowed = new ArrayList<>();
        for (String dim : allDims) {
            if (!blocked.contains(dim)) allowed.add(dim);
        }
        onConfirm.accept(entityId, allowed);
        visible = false;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
