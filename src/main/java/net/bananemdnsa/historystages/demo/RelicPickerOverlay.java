package net.bananemdnsa.historystages.demo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The stand-in addon's own Add widget, written from scratch against {@link PickerOverlay}.
 *
 * <p>This is tier 3 of the design's three: not a search list of the mod's, not a subclass of one,
 * but a panel the addon draws itself. Nothing here comes from HistoryStages except the interface
 * and the promise that whatever implements it gets rendered above the editor and fed input.
 *
 * <p>It exists because the free tier genuinely cannot express this pick. A relic entry needs a
 * relic <em>and</em> a rarity, and a list of ids can only hand back one string — so the mod's
 * pickers would need a second step. Here both are chosen in one gesture: pick the rarity along the
 * bottom, then click a relic.
 *
 * <p>Deliberately plain to read. An addon author looking for the shortest complete example of an
 * own widget should be able to follow every line of it.
 */
final class RelicPickerOverlay implements PickerOverlay {

    private static final int COLUMNS = 3;
    private static final int CHIP_W = 104;
    private static final int CHIP_H = 18;
    private static final int GAP = 4;
    private static final int PADDING = 8;
    private static final int TITLE_H = 14;
    private static final int RARITY_H = 20;
    private static final int VISIBLE_ROWS = 6;

    private final Supplier<Collection<String>> candidates;
    private final Supplier<Collection<String>> alreadyAdded;
    private final Consumer<String> onSelect;
    /** Where the chosen rarity is parked, because a picker can only hand back one string. */
    private final Consumer<String> onRarityChosen;

    private final List<String> shown = new ArrayList<>();
    private boolean visible;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int scrollRow;
    private int rarityIndex;

    RelicPickerOverlay(Supplier<Collection<String>> candidates,
                       Supplier<Collection<String>> alreadyAdded,
                       Consumer<String> onSelect,
                       Consumer<String> onRarityChosen) {
        this.candidates = candidates;
        this.alreadyAdded = alreadyAdded;
        this.onSelect = onSelect;
        this.onRarityChosen = onRarityChosen;
    }

    @Override
    public void show(int centerX, int centerY, int parentWidth) {
        shown.clear();
        shown.addAll(candidates.get());
        shown.removeAll(alreadyAdded.get());
        scrollRow = 0;

        panelW = PADDING * 2 + COLUMNS * CHIP_W + (COLUMNS - 1) * GAP;
        panelH = PADDING * 2 + TITLE_H + VISIBLE_ROWS * (CHIP_H + GAP) + RARITY_H;
        panelX = Math.max(4, centerX - panelW / 2);
        panelY = Math.max(4, centerY - panelH / 2);
        visible = true;
    }

    @Override
    public void hide() {
        visible = false;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setFilter(String filter) {
        // No search box: the demo's relic list is short enough to show whole.
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);
        g.drawString(font, Component.translatable("editor.historystages.demo.picker.title").getString(),
                panelX + PADDING, panelY + PADDING, 0xFFCC00, false);

        int top = panelY + PADDING + TITLE_H;
        for (int i = 0; i < VISIBLE_ROWS * COLUMNS; i++) {
            int index = scrollRow * COLUMNS + i;
            if (index >= shown.size()) break;
            int[] box = chipBox(i, top);
            boolean hovered = mouseX >= box[0] && mouseX < box[0] + CHIP_W
                    && mouseY >= box[1] && mouseY < box[1] + CHIP_H;
            g.fill(box[0], box[1], box[0] + CHIP_W, box[1] + CHIP_H,
                    hovered ? 0xFF3D3520 : 0xFF2A2A2A);
            g.fill(box[0], box[1], box[0] + 2, box[1] + CHIP_H, rarityColour());
            String label = shown.get(index);
            g.drawString(font, font.plainSubstrByWidth(label, CHIP_W - 12), box[0] + 6, box[1] + 5,
                    hovered ? 0xFFFFFF : 0xBBBBBB, false);
        }

        int rarityY = panelY + panelH - PADDING - RARITY_H + 4;
        for (int i = 0; i < RelicSetDep.RARITIES.size(); i++) {
            int[] box = rarityBox(i, rarityY);
            boolean active = i == rarityIndex;
            g.fill(box[0], box[1], box[0] + box[2], box[1] + CHIP_H,
                    active ? 0xFF3D3520 : 0xFF222222);
            g.drawString(font, RelicSetDep.RARITIES.get(i), box[0] + 6, box[1] + 5,
                    active ? 0xFFCC00 : 0x888888, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;

        // Outside the panel dismisses, the way every other picker in the editor behaves.
        if (mouseX < panelX || mouseX >= panelX + panelW
                || mouseY < panelY || mouseY >= panelY + panelH) {
            hide();
            return true;
        }

        int rarityY = panelY + panelH - PADDING - RARITY_H + 4;
        for (int i = 0; i < RelicSetDep.RARITIES.size(); i++) {
            int[] box = rarityBox(i, rarityY);
            if (mouseX >= box[0] && mouseX < box[0] + box[2]
                    && mouseY >= box[1] && mouseY < box[1] + CHIP_H) {
                rarityIndex = i;
                return true;
            }
        }

        int top = panelY + PADDING + TITLE_H;
        for (int i = 0; i < VISIBLE_ROWS * COLUMNS; i++) {
            int index = scrollRow * COLUMNS + i;
            if (index >= shown.size()) break;
            int[] box = chipBox(i, top);
            if (mouseX >= box[0] && mouseX < box[0] + CHIP_W
                    && mouseY >= box[1] && mouseY < box[1] + CHIP_H) {
                // The rarity first, because the tab reads it while handling the id.
                onRarityChosen.accept(RelicSetDep.RARITIES.get(rarityIndex));
                onSelect.accept(shown.get(index));
                hide();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible) return false;
        int maxRow = Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
        scrollRow = Math.max(0, Math.min(maxRow, scrollRow - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            hide();
            return true;
        }
        // Left and right cycle the rarity, so the whole widget is reachable without the mouse.
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            int step = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            int size = RelicSetDep.RARITIES.size();
            rarityIndex = (rarityIndex + step + size) % size;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseReleased() {
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        return false;
    }

    private int[] chipBox(int slot, int top) {
        int column = slot % COLUMNS;
        int row = slot / COLUMNS;
        return new int[] {
                panelX + PADDING + column * (CHIP_W + GAP),
                top + row * (CHIP_H + GAP) };
    }

    private int[] rarityBox(int index, int rarityY) {
        int width = (panelW - PADDING * 2) / RelicSetDep.RARITIES.size() - GAP;
        return new int[] { panelX + PADDING + index * (width + GAP), rarityY, width };
    }

    private int rarityColour() {
        return switch (rarityIndex) {
            case 1 -> 0xFF4FA3FF;
            case 2 -> 0xFFC77DFF;
            default -> 0xFF9E9E9E;
        };
    }
}
