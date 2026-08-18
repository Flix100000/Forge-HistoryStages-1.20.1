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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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

    /**
     * X of the row's value control. Derived from the label width so long labels push the
     * control right instead of overlapping it.
     */
    public int controlX(ConfigEditorScreen.ConfigEntry entry, int left) {
        Font font = Minecraft.getInstance().font;
        int labelWidth = font.width(Component.translatable(entry.labelKey).getString());
        return left + Math.max(labelWidth + 20, 180);
    }

    /** True when the cursor is over this row's clickable control area. */
    public boolean hitTest(ConfigEditorScreen.ConfigEntry entry, int left, int right, int y,
                           double mouseX, double mouseY) {
        if (mouseY < y || mouseY >= y + ENTRY_HEIGHT) return false;
        int controlX = controlX(entry, left);
        // An ENUM row draws a bordered button rather than filling the column, so its clickable
        // area stops where the box does — clicking empty space next to a control should do
        // nothing, the same as it does for every other widget in the editor.
        if (entry.type == ConfigEditorScreen.ConfigType.ENUM) {
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

        // Label
        String label = Component.translatable(entry.labelKey).getString();
        guiGraphics.drawString(font, label, left + 8 + Math.round(hp * 2.0f), y + 8,
                Fade.mix(0xFFCCCCCC, 0xFFFFFFFF, hp), false);

        // Value control — positioned further left for better readability
        int controlX = controlX(entry, left);

        switch (entry.type) {
            case BOOLEAN -> {
                boolean val = Boolean.parseBoolean(entry.value);
                String toggleText = val ? "✔ ON" : "✘ OFF";
                int toggleColor = val ? 0x55FF55 : 0xFF5555;
                boolean toggleHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                if (toggleHovered) toggleColor = val ? 0x88FF88 : 0xFF8888;
                guiGraphics.drawString(font, toggleText, controlX, y + 8, toggleColor, false);
            }
            case INTEGER, DOUBLE -> {
                guiGraphics.drawString(font, entry.value, controlX, y + 8, 0xDDDDDD, false);
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
                String display = "[" + count + " items] §7(click to edit)";
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case TAG_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(",").length;
                String display = "[" + count + " tags] §7(click to edit)";
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
                String display = "[" + count + " boosters] §7(click to edit)";
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case ITEM -> {
                // Render a 16x16 item icon + the item ID text
                ItemStack preview = resolveItemStack(entry.value);
                if (!preview.isEmpty()) {
                    guiGraphics.renderItem(preview, controlX, y + 3);
                }
                String itemDisplay = entry.value.isEmpty() ? "§7(none)" : entry.value;
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
                        enumLabel(entry.enumType, entry.value).getString(), bp, false, 0.0f);
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
                        colorHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
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
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }
}
