package net.bananemdnsa.historystages.compat.ftbquests.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.compat.ftbquests.StagePickerConfig;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Full-page searchable Global/Individual stage picker opened from a History Stage
 * task/reward. Styled to match the project's editor screens. The selection is
 * deferred: clicking a row only moves the highlight; the choice is applied when the
 * user clicks Back. Pressing Esc cancels without applying.
 */
public class StagePickerScreen extends Screen {

    private static final int HEADER_HEIGHT = 50;
    private static final int ROW_HEIGHT = 24;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_PAD = 8;
    private static final int TAB_Y = 30;
    private static final float SMALL_SCALE = 0.85f;

    private final Screen parent;
    private final Consumer<String> onChosen;

    // Pending selection (applied only on Back)
    private String selectedId;
    private boolean selectedIndividual;
    private boolean hasSelection;

    private boolean individualTab;
    private EditBox searchBox;
    private String filter = "";
    private final List<String> visible = new ArrayList<>();
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;

    private final String[] tabKeys = {
            "ftbquests.historystages.picker.tab.global",
            "ftbquests.historystages.picker.tab.individual"
    };
    private final int[] tabX = new int[2];
    private final int[] tabW = new int[2];

    public StagePickerScreen(Screen parent, String currentValue, Consumer<String> onChosen) {
        super(Component.translatable("ftbquests.historystages.picker.title"));
        this.parent = parent;
        this.onChosen = onChosen;
        this.hasSelection = currentValue != null && !currentValue.isEmpty();
        this.selectedId = StagePickerConfig.stripPrefix(currentValue);
        this.selectedIndividual = StagePickerConfig.isIndividual(currentValue);
        this.individualTab = this.selectedIndividual;
    }

    private int listTop() { return HEADER_HEIGHT; }
    private int listBottom() { return this.height - 40; }
    private int listLeft() { return 20; }
    private int listRight() { return this.width - 20; }

    @Override
    protected void init() {
        int gap = 2;
        int tabTotalWidth = 200;
        int tabStartX = this.width / 2 - tabTotalWidth / 2;
        int tabWidthEach = (tabTotalWidth - gap) / 2;
        int x = tabStartX;
        for (int i = 0; i < 2; i++) {
            tabX[i] = x;
            tabW[i] = tabWidthEach;
            x += tabWidthEach + gap;
        }

        searchBox = new EditBox(this.font, 14, 8, 112, 14,
                Component.translatable("ftbquests.historystages.picker.search"));
        searchBox.setMaxLength(128);
        searchBox.setBordered(false);
        searchBox.setValue(filter);
        searchBox.setResponder(v -> { filter = v; rebuild(); });
        addRenderableWidget(searchBox);

        addRenderableWidget(StyledButton.of(
                Component.translatable("ftbquests.historystages.picker.back"),
                b -> applyAndClose(), 10, this.height - 30, 100, 20));

        rebuild();
        scrollToSelected();
    }

    private void applyAndClose() {
        String prefixed = hasSelection
                ? (selectedIndividual ? StagePickerConfig.INDIVIDUAL_PREFIX : StagePickerConfig.GLOBAL_PREFIX) + selectedId
                : "";
        onChosen.accept(prefixed); // callback sets the value, saves, and returns to parent
    }

    private Map<String, StageEntry> entries() {
        return individualTab ? StageManager.getIndividualStages() : StageManager.getStages();
    }

    private List<String> order() {
        return individualTab ? StageManager.getIndividualStageOrder() : StageManager.getStageOrder();
    }

    private void rebuild() {
        String q = filter.toLowerCase().trim();
        Map<String, StageEntry> map = entries();
        visible.clear();
        for (String id : order()) {
            StageEntry e = map.get(id);
            String name = e != null ? e.getDisplayName() : id;
            if (q.isEmpty() || id.toLowerCase().contains(q) || name.toLowerCase().contains(q)) {
                visible.add(id);
            }
        }
        int listH = listBottom() - listTop();
        maxScroll = Math.max(0, visible.size() * ROW_HEIGHT - listH);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    /** If the pending selection is on the active tab and visible, center it. */
    private void scrollToSelected() {
        if (!hasSelection || individualTab != selectedIndividual) return;
        int idx = visible.indexOf(selectedId);
        if (idx < 0) return;
        int listH = listBottom() - listTop();
        scrollOffset = Math.max(0, Math.min(maxScroll, (double) idx * ROW_HEIGHT - listH / 2.0 + ROW_HEIGHT / 2.0));
    }

    private void switchTab(boolean individual) {
        if (individualTab == individual) return;
        individualTab = individual;
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        scrollOffset = 0;
        rebuild();
        scrollToSelected();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dy) {
        if (my >= listTop() && my <= listBottom()) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - dy * ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mx, my, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Unfocus search when clicking outside its box
        if (searchBox != null && searchBox.isFocused()
                && !(mx >= 10 && mx <= 130 && my >= 5 && my <= 24)) {
            searchBox.setFocused(false);
        }

        // Tab clicks (custom-drawn, like ConfigEditorScreen)
        if (my >= TAB_Y && my < TAB_Y + TAB_HEIGHT) {
            for (int i = 0; i < 2; i++) {
                if (mx >= tabX[i] && mx < tabX[i] + tabW[i]) {
                    switchTab(i == 1);
                    return true;
                }
            }
        }

        int listTop = listTop(), listBottom = listBottom(), listRight = listRight();

        // Scrollbar drag start
        if (maxScroll > 0 && mx >= listRight + 1 && mx <= listRight + 6
                && my >= listTop && my <= listBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(my, listTop, listBottom);
            return true;
        }

        // Row click: move the pending selection only (do NOT apply/close)
        if (button == 0 && mx >= listLeft() && mx <= listRight
                && my >= listTop && my < listBottom) {
            int idx = (int) ((my - listTop + scrollOffset) / ROW_HEIGHT);
            if (idx >= 0 && idx < visible.size()) {
                selectedId = visible.get(idx);
                selectedIndividual = individualTab;
                hasSelection = true;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(my, listTop(), listBottom());
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
        int listH = listBottom - listTop;
        int totalH = maxScroll + listH;
        int thumbHeight = Math.max(20, (int) ((float) listH / totalH * listH));
        float usableH = listH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - listTop - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // No-op — we draw our own background in render()
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // Editor-style search bar
        int searchX = 10, searchW = 120;
        g.fill(searchX, 5, searchX + searchW, 23, 0x25FFFFFF);
        g.fill(searchX, 23, searchX + searchW, 24, searchBox != null && searchBox.isFocused() ? 0xFFFFCC00 : 0xFF555555);
        if (filter.isEmpty() && (searchBox == null || !searchBox.isFocused())) {
            g.drawString(this.font, Component.translatable("ftbquests.historystages.picker.search").getString(),
                    searchX + 4, 10, 0x888888, false);
        }

        // Separator above tabs
        g.fill(10, TAB_Y - 2, this.width - 10, TAB_Y - 1, 0xFF555555);

        // Custom tabs (design + behavior copied from ConfigEditorScreen)
        for (int i = 0; i < 2; i++) {
            boolean active = (i == 1) == individualTab;
            boolean hovered = mx >= tabX[i] && mx < tabX[i] + tabW[i] && my >= TAB_Y && my < TAB_Y + TAB_HEIGHT;
            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            g.fill(tabX[i], TAB_Y, tabX[i] + tabW[i], TAB_Y + TAB_HEIGHT, bg);
            if (active) {
                g.fill(tabX[i], TAB_Y + TAB_HEIGHT - 2, tabX[i] + tabW[i], TAB_Y + TAB_HEIGHT, 0xFFFFCC00);
            }
            String label = Component.translatable(tabKeys[i]).getString();
            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            drawSmallText(g, label, tabX[i] + TAB_PAD, TAB_Y + 4, textColor);
        }

        // Separator below tabs
        g.fill(10, HEADER_HEIGHT - 2, this.width - 10, HEADER_HEIGHT - 1, 0xFF555555);

        int listTop = listTop(), listBottom = listBottom(), listLeft = listLeft(), listRight = listRight();
        g.enableScissor(listLeft, listTop, listRight, listBottom);
        Map<String, StageEntry> map = entries();
        for (int i = 0; i < visible.size(); i++) {
            int rowY = listTop - (int) scrollOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) continue;

            String id = visible.get(i);
            StageEntry e = map.get(id);
            boolean isSelected = hasSelection && individualTab == selectedIndividual && id.equals(selectedId);
            boolean hover = mx >= listLeft && mx <= listRight
                    && my >= Math.max(rowY, listTop) && my < Math.min(rowY + ROW_HEIGHT - 2, listBottom);

            if (isSelected) {
                g.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 2, 0x40FFCC00);
                g.fill(listLeft, rowY + ROW_HEIGHT - 3, listRight, rowY + ROW_HEIGHT - 2, 0xFFFFCC00);
            } else if (hover) {
                g.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT - 2, 0x25FFFFFF);
            }

            ItemStack icon = iconStack(e);
            if (!icon.isEmpty()) g.renderItem(icon, listLeft + 4, rowY + 3);
            String name = e != null ? e.getDisplayName() : id;
            g.drawString(this.font, name, listLeft + 26, rowY + 3, 0xFFFFFF);
            g.drawString(this.font, id, listLeft + 26, rowY + 13, 0xAAAAAA, false);
        }
        g.disableScissor();

        // Scrollbar (matches editor)
        if (maxScroll > 0) {
            int listH = listBottom - listTop;
            int barHeight = Math.max(20, (int) ((float) listH / (maxScroll + listH) * listH));
            int barY = listTop + (int) ((float) scrollOffset / maxScroll * (listH - barHeight));
            g.fill(listRight + 2, barY, listRight + 5, barY + barHeight, 0x80FFFFFF);
        }

        super.render(g, mx, my, pt);
    }

    private void drawSmallText(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        g.drawString(this.font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static ItemStack iconStack(StageEntry e) {
        String iconId = (e != null && !e.getIcon().isEmpty()) ? e.getIcon() : Config.COMMON.defaultStageIcon.get();
        if (iconId == null || iconId.isEmpty()) return ItemStack.EMPTY;
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(iconId));
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        } catch (Exception ex) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); } // Esc cancels without applying

    @Override
    public boolean isPauseScreen() { return false; }
}
