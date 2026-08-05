package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.client.editor.ConfigEditorScreen;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownChrome;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.data.graph.GraphColors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws and hit-tests a single config row — label on the left, type-specific control on the
 * right — for every screen that shows {@link ConfigEditorScreen.ConfigEntry} rows.
 *
 * <p>Rows only: scrolling, section headers, the scissor rectangle, the tab strip and the
 * scrollbar all stay with the owning screen. The one piece of shared geometry is
 * {@link #controlX}, which the render path and the click path must both go through so the
 * clickable area cannot drift away from the drawn control.
 */
public class ConfigRowList {

    /** Height of one config row. */
    public static final int ENTRY_HEIGHT = 24;
    /** Height of a section header band. */
    public static final int SECTION_HEADER_HEIGHT = 22;
    /** Vertical gap between two sections. */
    public static final int SECTION_GAP = 12;

    /** Vertical inset that centres an 18px dropdown button in the 24px row. */
    public static final int DROPDOWN_INSET_Y = (ENTRY_HEIGHT - 18) / 2;

    /** Label and value colour of a row whose value comes from a layer below it. */
    private static final int INHERITED_TEXT = 0xFF777777;
    private static final int INHERITED_TEXT_HOVER = 0xFF999999;

    /** Width of the clear-to-inherit × at the left edge of an overridden row. */
    public static final int CLEAR_WIDTH = 10;

    /**
     * Space kept clear at the left of every {@code clearable} row, whether or not it currently
     * draws a ×. Reserved rather than added on demand: a row switches between overridden and
     * inherited on a single click, and a label that jumped sideways each time would make the
     * whole list twitch.
     */
    private static final int CLEAR_GUTTER = CLEAR_WIDTH + 4;

    /** Left inset a row's label starts at, past the clear gutter when the row has one. */
    private static int textLeft(ConfigEditorScreen.ConfigEntry entry, int left) {
        return left + (entry.clearable ? CLEAR_GUTTER : 0);
    }

    /** Hover progress per config entry, keyed by the entry's config key. */
    private final Map<String, Anim> entryHover = new HashMap<>();
    /** Hover progress of the value control itself, which is narrower than its row. */
    private final Map<String, Anim> controlHover = new HashMap<>();

    /** Narrowest an ENUM row's button may get, whatever its options are. */
    public static final int DROPDOWN_MIN_WIDTH = 80;

    /**
     * Width of an ENUM row's dropdown button. Computed exactly as {@link EnumDropdown} computes
     * its own, so the popup lines up with the box the row drew.
     */
    public static int dropdownWidth(ConfigEditorScreen.ConfigEntry entry) {
        return EnumDropdown.computeWidth(entry.enumConstants,
                constant -> enumLabel(entry.enumType, constant), DROPDOWN_MIN_WIDTH);
    }

    /** Width reserved for the label column before the value control starts. */
    private int labelColumnWidth = DEFAULT_LABEL_COLUMN;

    /** What the config editor's tabs have always used; every screen keeps it unless it says so. */
    private static final int DEFAULT_LABEL_COLUMN = 180;

    /**
     * Narrows (or widens) the label column for this list.
     *
     * <p>The default is sized for the config tabs, which run the full width of the screen. A list
     * that shares its width with something else — the per-stage style editor gives half of it to
     * the preview — would push its controls off its own right edge with that much reserved for
     * nine short labels.
     */
    public void setLabelColumnWidth(int px) {
        this.labelColumnWidth = px;
    }

    /**
     * X of the row's value control. Derived from the label width so long labels push the
     * control right instead of overlapping it.
     */
    public int controlX(ConfigEditorScreen.ConfigEntry entry, int left) {
        Font font = Minecraft.getInstance().font;
        int labelWidth = font.width(Component.translatable(entry.labelKey).getString());
        return textLeft(entry, left) + Math.max(labelWidth + 20, labelColumnWidth);
    }

    /** Left edge of the clear-to-inherit ×, in the gutter ahead of the label. */
    public static int clearX(int left) {
        return left + 2;
    }

    /**
     * True when the cursor is over the clear ×. It sits in its own gutter left of the label, so
     * it cannot collide with the value control — but callers still ask this before
     * {@link #hitTest}, since a row's clickable area starts at the control and the two must be
     * resolved in a fixed order.
     */
    public boolean hitTestClear(ConfigEditorScreen.ConfigEntry entry, int left, int y,
                                double mouseX, double mouseY) {
        if (!entry.clearable || entry.inherited) return false;
        return mouseY >= y && mouseY < y + ENTRY_HEIGHT
                && mouseX >= clearX(left) && mouseX < clearX(left) + CLEAR_WIDTH;
    }

    /** True when the cursor is over this row's clickable control area. */
    public boolean hitTest(ConfigEditorScreen.ConfigEntry entry, int left, int right, int y,
                           double mouseX, double mouseY) {
        if (mouseY < y || mouseY >= y + ENTRY_HEIGHT) return false;
        int controlX = controlX(entry, left);
        // An ENUM row draws a bordered button rather than filling the column, so its clickable
        // area stops where the box does — clicking empty space next to a control should do
        // nothing, the same as it does for every other widget in the editor.
        // A varying row draws the hint in place of that box, so its width is not what the user
        // has in front of them to aim at.
        if (entry.type == ConfigEditorScreen.ConfigType.ENUM && !entry.varies) {
            return mouseX >= controlX && mouseX < controlX + dropdownWidth(entry);
        }
        return mouseX >= controlX && mouseX <= right - 5;
    }

    public void renderRow(GuiGraphics guiGraphics, ConfigEditorScreen.ConfigEntry entry,
                          int left, int y, int right, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;

        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
        // Keyed by the config key rather than the row index: sections collapse and the active
        // tab changes, so an index would carry one row's hover state over to a different setting.
        float hp = Ease.outCubic(entryHover.computeIfAbsent(entry.key, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        if (hp > 0.001f) {
            guiGraphics.fill(left, y, right, y + ENTRY_HEIGHT, Fade.rgba(0xFFFFFF, 0.082f * hp));
            // Gold edge, the same cue the stage list and the dropdowns use for a hovered row.
            guiGraphics.fill(left, y, left + 1, y + ENTRY_HEIGHT, Fade.rgba(0xFFCC00, hp * 0.8f));
        }

        // Label. An inherited row is dimmed as a whole — the value it shows belongs to
        // graph.toml, not to this stage, and the difference has to be visible at a glance.
        String label = Component.translatable(entry.labelKey).getString();
        guiGraphics.drawString(font, label, textLeft(entry, left) + 8 + Math.round(hp * 2.0f), y + 8,
                entry.inherited
                        ? Fade.mix(INHERITED_TEXT, INHERITED_TEXT_HOVER, hp)
                        : Fade.mix(0xFFCCCCCC, 0xFFFFFFFF, hp), false);

        // Value control — positioned further left for better readability
        int controlX = controlX(entry, left);

        // Before the type switch, because there is no value for a control to draw: a BOOLEAN row
        // would show a made-up OFF and an ENUM row would look up a lang key that does not exist.
        // Such a row is inherited by definition, so it needs no clear × either.
        if (entry.varies) {
            guiGraphics.drawString(font,
                    Component.translatable("editor.historystages.graph.style.varies").getString(),
                    controlX, y + 8, INHERITED_TEXT & 0xFFFFFF, false);
            return;
        }

        switch (entry.type) {
            case BOOLEAN -> {
                boolean val = Boolean.parseBoolean(entry.value);
                String toggleText = val ? "\u2714 ON" : "\u2718 OFF";
                int toggleColor = val ? 0x55FF55 : 0xFF5555;
                boolean toggleHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                if (toggleHovered) toggleColor = val ? 0x88FF88 : 0xFF8888;
                if (entry.inherited) toggleColor = INHERITED_TEXT & 0xFFFFFF;
                guiGraphics.drawString(font, toggleText, controlX, y + 8, toggleColor, false);
            }
            case INTEGER, DOUBLE -> {
                guiGraphics.drawString(font, entry.value, controlX, y + 8,
                        entry.inherited ? INHERITED_TEXT & 0xFFFFFF : 0xDDDDDD, false);
            }
            case STRING -> {
                String display = entry.value;
                int availWidth = right - controlX - 5;
                if (availWidth > 0 && font.width(display) > availWidth) {
                    display = font.plainSubstrByWidth(display, availWidth - 10) + "...";
                }
                guiGraphics.drawString(font, display, controlX, y + 8, 0xDDDDDD, false);
            }
            case ITEM_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(",").length;
                String display = "[" + count + " items] \u00A77(click to edit)";
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case TAG_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(",").length;
                String display = "[" + count + " tags] \u00A77(click to edit)";
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case EFFECT_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(";").length;
                String display = Component.translatable(
                        "editor.historystages.config.effects_summary", count).getString();
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case BOOSTER_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(";").length;
                String display = "[" + count + " boosters] \u00A77(click to edit)";
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case MULTI_STAGE_POLICY -> {
                boolean strict = !"LENIENT".equalsIgnoreCase(entry.value);
                String toggleText = strict ? "\u2714 STRICT" : "\u2718 LENIENT";
                int toggleColor = strict ? 0x55FF55 : 0xFFAA55;
                boolean toggleHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                if (toggleHovered) toggleColor = strict ? 0x88FF88 : 0xFFCC88;
                guiGraphics.drawString(font, toggleText, controlX, y + 8, toggleColor, false);
            }
            case ITEM -> {
                // Render a 16x16 item icon + the item ID text
                ItemStack preview = resolveItemStack(entry.value);
                if (!preview.isEmpty()) {
                    guiGraphics.renderItem(preview, controlX, y + 3);
                }
                String itemDisplay = entry.value.isEmpty() ? "\u00A77(none)" : entry.value;
                boolean itemHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(font, itemDisplay, controlX + 18, y + 8,
                        itemHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case ENUM -> {
                // The real control, not a label: this row opens a dropdown, so it has to look
                // like every other dropdown in the editor. The popup that appears on click is
                // positioned to sit right under this box.
                int bw = dropdownWidth(entry);
                boolean enumHovered = mouseX >= controlX && mouseX < controlX + bw
                        && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
                float bp = Ease.outCubic(controlHover.computeIfAbsent(entry.key, k -> new Anim())
                        .ramp(enumHovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
                DropdownChrome.drawButton(guiGraphics, font, controlX, y + DROPDOWN_INSET_Y,
                        bw, EnumDropdown.BUTTON_HEIGHT,
                        enumLabel(entry.enumType, entry.value).getString(), bp, false, 0.0f,
                        entry.inherited ? INHERITED_TEXT : 0xFFEEEEEE);
            }
            case SUBSCREEN -> {
                boolean subHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(font,
                        Component.translatable("editor.historystages.config.open_subscreen").getString(),
                        controlX, y + 8, subHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case TEXTURE -> {
                // Same shape as the ITEM row: the thing itself, then its id. A texture path
                // alone says nothing about what it looks like.
                boolean texHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                TextureAtlasSprite sprite = SearchableTextureList.spriteFor(entry.value);
                if (sprite != null) {
                    guiGraphics.blit(controlX, y + 4, 0, 16, 16, sprite);
                }
                String shown = entry.value.isEmpty()
                        ? Component.translatable("editor.historystages.config.texture_none").getString()
                        : entry.value.replace("textures/block/", "").replace(".png", "");
                guiGraphics.drawString(font, shown, controlX + 20, y + 8,
                        texHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case COLOR -> {
                // The hex alone is unreadable as a colour, so the row carries a swatch. Drawn
                // opaque: graph.toml stores no alpha, and a translucent chip over the row's
                // hover wash would read as a different colour than the graph actually uses.
                boolean colorHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.fill(controlX - 1, y + 6, controlX + 11, y + 18, 0xFF555555);
                guiGraphics.fill(controlX, y + 7, controlX + 10, y + 17,
                        0xFF000000 | GraphColors.parse(entry.value, 0));
                guiGraphics.drawString(font, entry.value, controlX + 16, y + 8,
                        entry.inherited ? INHERITED_TEXT & 0xFFFFFF
                                : (colorHovered ? 0xFFCC00 : 0xDDDDDD), false);
            }
        }

        // The way back to inheriting, in the gutter ahead of the label. Only on a row that is
        // actually overriding something, so the column of × marks reads down the list as the
        // answer to "what does this stage set?".
        if (entry.clearable && !entry.inherited) {
            boolean clearHovered = mouseX >= clearX(left) && mouseX < clearX(left) + CLEAR_WIDTH
                    && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
            guiGraphics.drawString(font, "✕", clearX(left) + 2, y + 8,
                    clearHovered ? 0xFFFF6666 : 0xFF888888, false);
        }
    }

    /**
     * Display text for an enum constant, e.g. NodeShape/ROUNDED becomes the value of
     * {@code editor.historystages.enum.nodeshape.rounded}.
     *
     * <p>The type is part of the key because constant names repeat across the graph's enums:
     * {@code SOLID} is both an edge style and a canvas background. English calls both "solid",
     * German calls one "durchgezogen" and the other "einfarbig", so one shared key would force
     * a wrong translation on one of them.
     */
    public static Component enumLabel(String enumType, String constant) {
        String type = enumType == null ? "unknown" : enumType.toLowerCase(java.util.Locale.ROOT);
        return Component.translatable(
                "editor.historystages.enum." + type + "." + constant.toLowerCase(java.util.Locale.ROOT));
    }

    private static ItemStack resolveItemStack(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }
}
