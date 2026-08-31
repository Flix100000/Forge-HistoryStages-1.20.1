package net.bananemdnsa.historystages.client.editor.widget.dropdown;

import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * An {@link EnumDropdown} dressed as a {@link PickerOverlay}, so a tab can open one over the
 * editor's content the same way it opens the Add picker.
 *
 * <p>A dropdown inside a scrolling, scissored row list cannot draw its own popup: the host clips
 * the content area, and the popup would be cut off at the first row near the bottom. The host
 * already renders whatever {@code activeOverlay()} hands back after everything else and routes
 * input to it first — which is exactly the treatment a popup needs. This is the adapter between
 * the two.
 *
 * <p>Only the popup is drawn here. The collapsed control is the row's own slot, drawn by the row
 * list where the row is, so it scrolls with it.
 */
public final class DropdownOverlay implements PickerOverlay {

    private final EnumDropdown dropdown;

    public DropdownOverlay(EnumDropdown dropdown) {
        this.dropdown = dropdown;
    }

    public EnumDropdown dropdown() {
        return dropdown;
    }

    /**
     * Opens the popup under the rectangle that was clicked.
     *
     * <p>{@link EnumDropdown} hangs its popup off the bottom of its own button, so the anchor is
     * placed such that the button's bottom edge lands on the slot's — the popup then appears just
     * below the row, and clicking the slot again folds it back up.
     */
    public void openAt(int x, int y, int height) {
        dropdown.setPosition(x, y + height - EnumDropdown.BUTTON_HEIGHT);
        dropdown.expand();
    }

    /** Whether the popup still has something to draw — see {@link EnumDropdown#isShowing()}. */
    public boolean isShowing() {
        return dropdown.isShowing();
    }

    @Override
    public void show(int centerX, int centerY, int parentWidth) {
        // Position comes from openAt: this popup belongs to a row, not to the screen's centre.
        dropdown.expand();
    }

    @Override
    public void hide() {
        dropdown.close();
    }

    @Override
    public boolean isVisible() {
        return dropdown.isExpanded();
    }

    @Override
    public void setFilter(String filter) {
        // Three options and no search field.
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        dropdown.renderPopup(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        return dropdown.mouseClicked(mouseX, mouseY);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // The popup is pinned to a row. Letting the content scroll under it would leave it
        // pointing at whichever row slid into that place.
        dropdown.close();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) dropdown.close();
        return true;
    }

    @Override
    public boolean charTyped(char c) {
        return true;
    }
}
