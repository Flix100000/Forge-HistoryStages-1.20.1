package net.bananemdnsa.historystages.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * An 18x18 icon button cut from the pedestal button sheet. The sheet is three columns
 * (play, pause, xp) by two rows (normal, hovered), so a column plus the hover state picks
 * the face. The column is a supplier because the start/pause button changes face with the
 * research state, not with anything the widget itself knows.
 *
 * <p>The same goes for whether it can be pressed at all: the pedestal decides that, and it
 * changes while the screen is open, so the widget asks every frame rather than being told
 * once at construction. A button that cannot be pressed is drawn with the pressed-in face,
 * which is also the hover face — the sheet has no third row, and "sunken" reads as
 * unavailable well enough that inventing a tint on top would only muddy it.
 */
public class PedestalIconButton extends AbstractButton {

    private final ResourceLocation sheet;
    private final IntSupplier column;
    private final BooleanSupplier enabled;
    private final Runnable onPress;

    /** {@code tooltip} may be null for a button whose icon speaks for itself. */
    public PedestalIconButton(int x, int y, ResourceLocation sheet, IntSupplier column,
                              BooleanSupplier enabled, Component tooltip, Runnable onPress) {
        super(x, y, PedestalLayout.ICON_SIZE, PedestalLayout.ICON_SIZE,
                tooltip != null ? tooltip : Component.empty());
        this.sheet = sheet;
        this.column = column;
        this.enabled = enabled;
        this.onPress = onPress;
        if (tooltip != null) {
            this.setTooltip(Tooltip.create(tooltip));
        }
    }

    @Override
    public void onPress() {
        this.onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Refreshed here because the pedestal's state changes underneath an open screen.
        // AbstractWidget.mouseClicked tests `active`, so keeping it current is what actually
        // blocks the click — the face below is only what the player sees.
        this.active = this.enabled.getAsBoolean();

        // A button that cannot be pressed sits pressed in, and so does one under the cursor.
        // Hover only, not isHoveredOrFocused(): a widget keeps focus after being clicked, so
        // reacting to focus would leave the button looking stuck down for the rest of the
        // screen's life even though it still works.
        boolean pressedIn = !this.active || this.isHovered();

        int u = this.column.getAsInt() * PedestalLayout.ICON_SIZE;
        int v = pressedIn ? PedestalLayout.ICON_SIZE : 0;
        guiGraphics.blit(this.sheet, this.getX(), this.getY(), u, v,
                PedestalLayout.ICON_SIZE, PedestalLayout.ICON_SIZE, 54, 36);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
