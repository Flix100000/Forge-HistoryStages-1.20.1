package net.bananemdnsa.historystages.client.editor.widget.list;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * What a screen needs from a picker overlay: show it, draw it, hand it input, ask whether it is
 * still up.
 *
 * <p>Exists because {@link SearchableItemList} grew up separately from
 * {@link AbstractSearchableList} and shares no supertype with it, even though the two offer the
 * same surface. A screen that can open either — the config editor opens the item picker for ITEM
 * rows and the texture picker for TEXTURE ones — would otherwise need two fields and two copies
 * of every dismiss, render and input path.
 *
 * <p>The implementations already had every method; this only names the set.
 */
public interface PickerOverlay {

    void show(int centerX, int centerY, int parentWidth);

    void hide();

    boolean isVisible();

    /** Resets what the picker is filtered by, so it opens showing everything. */
    void setFilter(String filter);

    void render(GuiGraphics g, Font font, int mouseX, int mouseY);

    boolean mouseClicked(double mouseX, double mouseY);

    boolean mouseDragged(double mouseX, double mouseY);

    boolean mouseReleased();

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);

    boolean keyPressed(int keyCode);

    boolean charTyped(char c);
}
