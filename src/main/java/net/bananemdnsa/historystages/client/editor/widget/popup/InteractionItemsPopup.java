package net.bananemdnsa.historystages.client.editor.widget.popup;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Popup for an interactionlock entry's held-item filter. The lock only bites while the player
 * holds one of the listed items; an empty list means "any held item" (the default).
 *
 * <p>This is a thin view over the editor's live filter list — rows are edited through the
 * right-click menu the screen builds via {@link Handler}, so there is no confirm/cancel working
 * copy to keep in sync.
 */
public class InteractionItemsPopup {

    /** Editor hooks — the popup itself owns no data. */
    public interface Handler {
        /** Current filter entries for this entity, or null when none are set. */
        List<ItemEntry> items(String entityId);
        /** Right-click on a row: the screen opens its context menu at the given screen position. */
        void openRowMenu(String entityId, int index, int mouseX, int mouseY);
        void addItem(String entityId);
        void addTag(String entityId);
    }

    private static final int PAD        = 8;
    private static final int WIDTH      = 300;
    private static final int HEADER_H   = 18;
    private static final int HINT_H     = 10;
    private static final int ROW_H      = 18;
    private static final int ROW_GAP    = 2;
    private static final int VISIBLE_ROWS = 6;
    private static final int FOOTER_H   = 20;
    private static final int BTN_H      = 14;
    private static final int SCROLLBAR_W = 4;

    private static final long MARQUEE_DELAY_MS = Timing.MARQUEE_DELAY_MS;
    private static final float MARQUEE_SPEED = Timing.MARQUEE_SPEED;

    private final Handler handler;
    /** Resolved display stacks, keyed by filter entry ID (plain item or "#tag"). */
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    private boolean visible = false;
    private String entityId = null;
    private int scrollRow = 0;
    private int cachedX, cachedY, cachedW, cachedH;

    private int hoveredRowIndex = -1;
    private long rowHoverStartTime = 0;
    private boolean draggingScrollbar = false;

    public InteractionItemsPopup(Handler handler) {
        this.handler = handler;
    }

    public boolean isVisible() { return visible; }

    public void hide() { visible = false; }

    public void show(String entityId) {
        this.entityId = entityId;
        this.scrollRow = 0;
        this.hoveredRowIndex = -1;
        this.visible = true;
    }

    private List<ItemEntry> entries() {
        return handler.items(entityId);
    }

    private int entryCount() {
        List<ItemEntry> list = entries();
        return list == null ? 0 : list.size();
    }

    private int maxScrollRow() {
        return Math.max(0, entryCount() - VISIBLE_ROWS);
    }

    private int visibleRows() {
        return Math.max(1, Math.min(VISIBLE_ROWS, entryCount()));
    }

    private int listTopY() {
        return cachedY + HEADER_H + HINT_H + 4;
    }

    private int scrollbarX() {
        return cachedX + PAD + (cachedW - PAD * 2) + 1;
    }

    private int scrollbarHeight(int rows) {
        return rows * ROW_H + (rows - 1) * ROW_GAP;
    }

    private int thumbHeight(int rows, int sbH) {
        return Math.max(8, (int) ((float) rows / entryCount() * sbH));
    }

    /** Maps a mouse Y on the scrollbar to a scroll row, centring the thumb on the cursor. */
    private void updateScrollFromMouse(double mouseY, int sbTop, int sbH, int rows) {
        int max = maxScrollRow();
        if (max <= 0) return;
        int thumbH = thumbHeight(rows, sbH);
        float usable = sbH - thumbH;
        if (usable <= 0) return;
        float ratio = (float) (mouseY - sbTop - thumbH / 2.0) / usable;
        ratio = Math.max(0f, Math.min(1f, ratio));
        scrollRow = Math.max(0, Math.min(max, Math.round(ratio * max)));
    }

    /**
     * Display stack for a filter entry: the item itself for plain IDs, or the first item of the
     * tag for "#tag" entries. Empty when it cannot be resolved (unknown ID or empty tag).
     */
    private ItemStack iconFor(String id) {
        ItemStack cached = iconCache.get(id);
        if (cached != null) return cached;

        ItemStack stack = ItemStack.EMPTY;
        try {
            if (id.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(id.substring(1));
                if (rl != null) {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, rl);
                    var tag = BuiltInRegistries.ITEM.getTag(tagKey);
                    if (tag.isPresent()) {
                        for (Holder<Item> holder : tag.get()) {
                            stack = new ItemStack(holder.value());
                            break;
                        }
                    }
                }
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                }
            }
        } catch (Exception ignored) {}

        iconCache.put(id, stack);
        return stack;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        int rows = Math.max(1, Math.min(VISIBLE_ROWS, entryCount()));
        int contentH = rows * ROW_H + (rows - 1) * ROW_GAP;

        int popupW = WIDTH;
        int popupH = HEADER_H + HINT_H + 4 + contentH + 4 + FOOTER_H;
        int popupX = g.guiWidth() / 2 - popupW / 2;
        int popupY = g.guiHeight() / 2 - popupH / 2;

        cachedX = popupX; cachedY = popupY; cachedW = popupW; cachedH = popupH;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        g.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        g.drawCenteredString(font,
                Component.translatable("editor.historystages.interaction_items.title"),
                popupX + popupW / 2, popupY + 5, 0xFFFFFFFF);
        int accentW = 40;
        int accentX = popupX + (popupW - accentW) / 2;
        g.fill(accentX, popupY + 15, accentX + accentW, popupY + 16, 0xFFFFCC00);

        Component hint = entryCount() == 0
                ? Component.translatable("editor.historystages.interaction_items.hint_empty")
                : Component.translatable("editor.historystages.interaction_items.hint");
        g.drawCenteredString(font, hint, popupX + popupW / 2, popupY + HEADER_H, 0x888888);

        int listY = popupY + HEADER_H + HINT_H + 4;
        int rowX = popupX + PAD;
        int rowW = popupW - PAD * 2;
        List<ItemEntry> list = entries();

        int currentHoveredRow = -1;

        for (int i = 0; i < rows; i++) {
            int index = scrollRow + i;
            int ry = listY + i * (ROW_H + ROW_GAP);

            if (list == null || index >= list.size()) {
                g.fill(rowX, ry, rowX + rowW, ry + ROW_H, 0x10FFFFFF);
                continue;
            }

            ItemEntry entry = list.get(index);
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= ry && mouseY < ry + ROW_H;
            if (hovered) currentHoveredRow = index;

            g.fill(rowX, ry, rowX + rowW, ry + ROW_H, hovered ? 0x25FFFFFF : 0x10FFFFFF);
            g.fill(rowX, ry + ROW_H - 1, rowX + rowW, ry + ROW_H, hovered ? 0x60FFCC00 : 0x20FFFFFF);

            // Item icon
            ItemStack icon = iconFor(entry.getId());
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(rowX + 2, ry + 1, 0);
                g.pose().scale(0.85f, 0.85f, 1.0f);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
            }

            String badge = entry.hasNbt() ? "§e[NBT]" : "";
            int badgeW = badge.isEmpty() ? 0 : font.width(badge) + 4;
            if (!badge.isEmpty()) {
                g.drawString(font, badge, rowX + rowW - badgeW, ry + 5, 0xFFFFFF, false);
            }

            int textStartX = rowX + 20;
            int textAvailW = rowX + rowW - badgeW - 4 - textStartX;
            String text = entry.getId();
            int textW = font.width(text);
            int textColor = hovered ? 0xFFFFFF : 0xCCCCCC;

            if (textW > textAvailW && hovered && index == hoveredRowIndex) {
                long elapsed = System.currentTimeMillis() - rowHoverStartTime;
                if (elapsed > MARQUEE_DELAY_MS) {
                    float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                    int maxMarquee = textW - textAvailW + 10;
                    float cycle = (float) maxMarquee * 2;
                    float pos = scrollProg % cycle;
                    int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                    g.enableScissor(textStartX, ry, textStartX + textAvailW, ry + ROW_H);
                    g.drawString(font, text, textStartX - scrollOff, ry + 5, textColor, false);
                    g.disableScissor();
                } else {
                    g.drawString(font, font.plainSubstrByWidth(text, textAvailW - 8) + "...",
                            textStartX, ry + 5, textColor, false);
                }
            } else if (textW > textAvailW) {
                g.drawString(font, font.plainSubstrByWidth(text, textAvailW - 8) + "...",
                        textStartX, ry + 5, textColor, false);
            } else {
                g.drawString(font, text, textStartX, ry + 5, textColor, false);
            }
        }

        if (currentHoveredRow != hoveredRowIndex) {
            hoveredRowIndex = currentHoveredRow;
            rowHoverStartTime = System.currentTimeMillis();
        }

        // Scrollbar — drag the thumb or use the wheel
        if (maxScrollRow() > 0) {
            int sbX = scrollbarX();
            int sbTop = listY;
            int sbH = scrollbarHeight(rows);
            boolean sbHovered = mouseX >= sbX - 2 && mouseX <= sbX + SCROLLBAR_W + 2
                    && mouseY >= sbTop && mouseY < sbTop + sbH;
            g.fill(sbX, sbTop, sbX + SCROLLBAR_W, sbTop + sbH, 0xFF252525);
            int thumbH = thumbHeight(rows, sbH);
            int thumbY = sbTop + (int) ((float) scrollRow / maxScrollRow() * (sbH - thumbH));
            g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH,
                    (draggingScrollbar || sbHovered) ? 0xFFCCCCCC : 0xFF888888);
        }

        int btnY = popupY + popupH - BTN_H - 6;
        int smallW = 60;
        int addItemX = popupX + PAD;
        int addTagX = addItemX + smallW + 4;
        int doneW = 48;
        int doneX = popupX + popupW - doneW - PAD;

        drawButton(g, font, addItemX, btnY, smallW,
                Component.translatable("editor.historystages.interaction_items.add_item").getString(),
                isIn(mouseX, mouseY, addItemX, btnY, smallW), false);
        drawButton(g, font, addTagX, btnY, smallW,
                Component.translatable("editor.historystages.interaction_items.add_tag").getString(),
                isIn(mouseX, mouseY, addTagX, btnY, smallW), false);
        drawButton(g, font, doneX, btnY, doneW,
                Component.translatable("editor.historystages.lock_actions.btn_done").getString(),
                isIn(mouseX, mouseY, doneX, btnY, doneW), true);
    }

    private void drawButton(GuiGraphics g, Font font, int x, int y, int w, String label, boolean hovered, boolean accent) {
        int bg = accent
                ? (hovered ? 0x50FFCC00 : 0x25FFCC00)
                : (hovered ? 0x25FFFFFF : 0x10FFFFFF);
        int line = accent
                ? (hovered ? 0xFFFFCC00 : 0x80FFCC00)
                : (hovered ? 0x80FFFFFF : 0x40FFFFFF);
        g.fill(x, y, x + w, y + BTN_H, bg);
        g.fill(x, y + BTN_H - 1, x + w, y + BTN_H, line);
        g.drawCenteredString(font, label, x + w / 2, y + 3, hovered ? 0xFFFFFF : 0xCCCCCC);
    }

    private boolean isIn(double mouseX, double mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BTN_H;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        int popupX = cachedX, popupY = cachedY, popupW = cachedW, popupH = cachedH;
        if (popupW == 0) return true;

        int btnY = popupY + popupH - BTN_H - 6;
        int smallW = 60;
        int addItemX = popupX + PAD;
        int addTagX = addItemX + smallW + 4;
        int doneW = 48;
        int doneX = popupX + popupW - doneW - PAD;

        if (button == 0) {
            if (isIn(mouseX, mouseY, addItemX, btnY, smallW)) {
                playClick();
                handler.addItem(entityId);
                return true;
            }
            if (isIn(mouseX, mouseY, addTagX, btnY, smallW)) {
                playClick();
                handler.addTag(entityId);
                return true;
            }
            if (isIn(mouseX, mouseY, doneX, btnY, doneW)) {
                playClick();
                visible = false;
                return true;
            }
        }

        int rows = visibleRows();
        int listY = listTopY();
        int rowX = popupX + PAD;
        int rowW = popupW - PAD * 2;
        List<ItemEntry> list = entries();

        if (button == 0 && maxScrollRow() > 0) {
            int sbX = scrollbarX();
            int sbH = scrollbarHeight(rows);
            if (mouseX >= sbX - 2 && mouseX <= sbX + SCROLLBAR_W + 2
                    && mouseY >= listY && mouseY < listY + sbH) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY, sbH, rows);
                return true;
            }
        }

        for (int i = 0; i < rows; i++) {
            int index = scrollRow + i;
            if (list == null || index >= list.size()) break;
            int ry = listY + i * (ROW_H + ROW_GAP);
            if (mouseX < rowX || mouseX >= rowX + rowW || mouseY < ry || mouseY >= ry + ROW_H) continue;

            if (button == 1) {
                playClick();
                handler.openRowMenu(entityId, index, (int) mouseX, (int) mouseY);
            }
            return true;
        }

        if (mouseX < popupX || mouseX > popupX + popupW || mouseY < popupY || mouseY > popupY + popupH) {
            visible = false;
        }
        return true;
    }

    /** Keeps the scroll offset valid after entries were removed. */
    public void clampScroll() {
        int max = maxScrollRow();
        if (scrollRow > max) scrollRow = max;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        int rows = visibleRows();
        updateScrollFromMouse(mouseY, listTopY(), scrollbarHeight(rows), rows);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!visible) return false;
        if (mouseX < cachedX || mouseX > cachedX + cachedW || mouseY < cachedY || mouseY > cachedY + cachedH) {
            return false;
        }
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) scrollY));
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC
            visible = false;
            return true;
        }
        return false;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
