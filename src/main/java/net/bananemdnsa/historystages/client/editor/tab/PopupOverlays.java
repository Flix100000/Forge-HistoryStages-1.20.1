package net.bananemdnsa.historystages.client.editor.tab;

import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.widget.popup.DimensionFilterPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.InteractionActionsPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.SpawnSourcesPopup;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Presents the mod's filter popups as {@link PickerOverlay}s, so a host can render them and feed
 * them input without knowing which of the three it has.
 *
 * <p>The popups predate that interface and each takes its own arguments in {@code show}, which is
 * why they cannot implement it directly. The adapter lives here rather than on the popups so the
 * popups stay what they are — their own callers pass those arguments and would gain nothing.
 *
 * <p>Each adapter keeps the arguments its popup's own {@code show} needs, so the host can place
 * it: the host is the only thing that knows where the middle of the screen is, and a popup told to
 * appear at (0, 0) lands in the corner.
 */
public final class PopupOverlays {

    private PopupOverlays() {}

    public static PickerOverlay wrap(DimensionFilterPopup popup, String entryId, java.util.List<String> current) {
        return new Adapter() {
            @Override
            public void show(int centerX, int centerY, int parentWidth) {
                popup.show(entryId, current, centerX, centerY);
            }

            @Override
            public boolean isVisible() {
                return popup.isVisible();
            }

            @Override
            public void hide() {
                popup.hide();
            }

            @Override
            public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
                popup.render(g, font, mouseX, mouseY);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY) {
                return popup.mouseClicked(mouseX, mouseY);
            }

            @Override
            public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
                return popup.mouseScrolled(mouseX, mouseY, scrollY);
            }

            @Override
            public boolean keyPressed(int keyCode) {
                return popup.keyPressed(keyCode);
            }
        };
    }

    public static PickerOverlay wrap(SpawnSourcesPopup popup, String entryId, java.util.List<String> current) {
        return new Adapter() {
            @Override
            public void show(int centerX, int centerY, int parentWidth) {
                // Centres itself; the coordinates are accepted and ignored.
                popup.show(entryId, current);
            }

            @Override
            public boolean isVisible() {
                return popup.isVisible();
            }

            @Override
            public void hide() {
                popup.hide();
            }

            @Override
            public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
                popup.render(g, font, mouseX, mouseY);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY) {
                return popup.mouseClicked(mouseX, mouseY);
            }

            @Override
            public boolean keyPressed(int keyCode) {
                return popup.keyPressed(keyCode);
            }
        };
    }

    public static PickerOverlay wrap(InteractionActionsPopup popup, String entryId, java.util.List<String> current) {
        return new Adapter() {
            @Override
            public void show(int centerX, int centerY, int parentWidth) {
                // Centres itself; the coordinates are accepted and ignored.
                popup.show(entryId, current);
            }

            @Override
            public boolean isVisible() {
                return popup.isVisible();
            }

            @Override
            public void hide() {
                popup.hide();
            }

            @Override
            public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
                popup.render(g, font, mouseX, mouseY);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY) {
                return popup.mouseClicked(mouseX, mouseY);
            }

            @Override
            public boolean keyPressed(int keyCode) {
                return popup.keyPressed(keyCode);
            }
        };
    }

    /**
     * The parts every one of the three answers the same way.
     *
     * <p>None of them has a text field or a draggable part, so those three methods say no rather
     * than pretending. A popup that grows one has to override here, and the compiler will not
     * remind anyone — which is why it is written down.
     */
    private abstract static class Adapter implements PickerOverlay {

        @Override
        public void setFilter(String filter) {
            // These popups have nothing to filter.
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
            return false;
        }

        @Override
        public boolean charTyped(char c) {
            return false;
        }
    }
}
