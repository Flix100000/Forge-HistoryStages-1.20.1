package net.bananemdnsa.historystages.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Screen for editing NBT criteria on an item entry.
 * Shows a tree of possible NBT properties with checkboxes and value editors.
 */
public class NbtItemEditScreen extends Screen {
    private final Screen parent;
    private final String itemId;
    private final JsonObject currentNbt;
    private final Consumer<JsonObject> onSave;

    // NBT property definitions — built once, persisted across init() calls
    private final List<NbtProperty> properties = new ArrayList<>();
    private boolean propertiesBuilt = false;
    private double scrollOffset = 0;
    private int maxScroll = 0;

    // Layout
    private static final int PADDING = 20;
    private static final int ROW_HEIGHT = 22;
    private static final int INDENT = 16;
    private static final int CHECKBOX_SIZE = 12;
    private static final int HEADER_HEIGHT = 60;

    // Validation warnings
    private List<String> validationWarnings = new ArrayList<>();
    private boolean showingWarnings = false;

    // Scrollbar dragging
    private boolean draggingScrollbar = false;

    // Cached suggestion lists
    private static List<String> enchantmentIds = null;
    private static List<String> potionIds = null;

    public NbtItemEditScreen(Screen parent, String itemId, JsonObject currentNbt, Consumer<JsonObject> onSave) {
        super(Component.literal("NBT Editor"));
        this.parent = parent;
        this.itemId = itemId;
        this.currentNbt = currentNbt;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        // Only build properties once — not on every init() call (screen switches)
        if (!propertiesBuilt) {
            buildPropertyTree();
            loadCurrentValues();
            propertiesBuilt = true;
        }

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> this.minecraft.setScreen(parent),
                PADDING, this.height - 30, 60, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.literal("Save NBT"),
                btn -> saveAndClose(),
                this.width / 2 - 50, this.height - 30, 100, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.literal("Clear All"),
                btn -> {
                    for (NbtProperty p : properties) {
                        p.enabled = false;
                        p.enchantments.clear();
                        p.stringListValues.clear();
                        p.value = null;
                        for (NbtProperty child : p.children) {
                            child.enabled = false;
                            child.value = null;
                            child.stringListValues.clear();
                        }
                    }
                },
                this.width - PADDING - 80, this.height - 30, 80, 20));

        updateMaxScroll();
    }

    private void buildPropertyTree() {
        properties.clear();

        // Enchantments
        properties.add(new NbtProperty("Enchantments", NbtType.ENCHANTMENT_LIST, "Enchantments on the item"));

        // StoredEnchantments (enchanted books)
        properties.add(new NbtProperty("StoredEnchantments", NbtType.ENCHANTMENT_LIST, "Stored enchantments (books)"));

        // CustomModelData
        properties.add(new NbtProperty("CustomModelData", NbtType.INTEGER, "Custom model data (resource packs)"));

        // display compound
        NbtProperty display = new NbtProperty("display", NbtType.COMPOUND, "Display properties");
        display.children.add(new NbtProperty("Name", NbtType.STRING, "Custom item name (JSON text)"));
        display.children.add(new NbtProperty("Lore", NbtType.STRING_LIST, "Custom lore lines (JSON text)"));
        properties.add(display);

        // Potion
        properties.add(new NbtProperty("Potion", NbtType.STRING, "Potion type ID"));

        // Unbreakable
        properties.add(new NbtProperty("Unbreakable", NbtType.BOOLEAN, "Item cannot break"));

        // RepairCost
        properties.add(new NbtProperty("RepairCost", NbtType.INTEGER, "Anvil repair cost"));
    }

    private void loadCurrentValues() {
        if (currentNbt == null) return;

        // Collect known property keys
        java.util.Set<String> knownKeys = new java.util.HashSet<>();
        for (NbtProperty prop : properties) {
            knownKeys.add(prop.key);
            if (currentNbt.has(prop.key)) {
                prop.enabled = true;
                loadPropertyValue(prop, currentNbt);
            }
            if (prop.type == NbtType.COMPOUND && currentNbt.has(prop.key)) {
                JsonObject compound = currentNbt.getAsJsonObject(prop.key);
                for (NbtProperty child : prop.children) {
                    if (compound.has(child.key)) {
                        child.enabled = true;
                        loadChildValue(child, compound);
                    }
                }
            }
        }

        // Load unknown keys as custom NBT properties
        for (var entry : currentNbt.entrySet()) {
            if (!knownKeys.contains(entry.getKey())) {
                NbtProperty custom = new NbtProperty(entry.getKey(), NbtType.STRING, "Custom NBT key");
                custom.enabled = true;
                if (entry.getValue().isJsonPrimitive()) {
                    custom.value = entry.getValue().getAsString();
                } else {
                    custom.value = entry.getValue().toString();
                }
                properties.add(custom);
            }
        }
    }

    private void loadPropertyValue(NbtProperty prop, JsonObject source) {
        switch (prop.type) {
            case INTEGER, STRING -> {
                if (source.get(prop.key).isJsonPrimitive()) {
                    prop.value = source.get(prop.key).getAsString();
                }
            }
            case BOOLEAN -> prop.value = source.get(prop.key).getAsBoolean() ? "true" : "false";
            case ENCHANTMENT_LIST -> {
                if (source.get(prop.key).isJsonArray()) {
                    JsonArray arr = source.getAsJsonArray(prop.key);
                    prop.enchantments.clear();
                    for (var el : arr) {
                        if (el.isJsonObject()) {
                            JsonObject ench = el.getAsJsonObject();
                            String id = ench.has("id") ? ench.get("id").getAsString() : "";
                            String lvl = ench.has("lvl") ? ench.get("lvl").getAsString() : "1";
                            prop.enchantments.add(new EnchantmentEntry(id, lvl));
                        }
                    }
                }
            }
            case STRING_LIST -> {
                if (source.get(prop.key).isJsonArray()) {
                    prop.stringListValues.clear();
                    for (var el : source.getAsJsonArray(prop.key)) {
                        prop.stringListValues.add(el.getAsString());
                    }
                }
            }
            default -> {}
        }
    }

    private void loadChildValue(NbtProperty child, JsonObject compound) {
        switch (child.type) {
            case STRING -> {
                if (compound.get(child.key).isJsonPrimitive()) {
                    child.value = compound.get(child.key).getAsString();
                }
            }
            case STRING_LIST -> {
                if (compound.get(child.key).isJsonArray()) {
                    child.stringListValues.clear();
                    for (var el : compound.getAsJsonArray(child.key)) {
                        child.stringListValues.add(el.getAsString());
                    }
                }
            }
            default -> loadPropertyValue(child, compound);
        }
    }

    private void updateMaxScroll() {
        int contentHeight = calculateContentHeight();
        int visibleHeight = this.height - HEADER_HEIGHT - 50;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int calculateContentHeight() {
        int height = 0;
        for (NbtProperty prop : properties) {
            height += ROW_HEIGHT;
            if (prop.type == NbtType.ENCHANTMENT_LIST && prop.enabled) {
                height += prop.enchantments.size() * ROW_HEIGHT;
                height += ROW_HEIGHT; // Add button
            }
            if (prop.type == NbtType.STRING_LIST && prop.enabled && prop.children.isEmpty()) {
                height += prop.stringListValues.size() * ROW_HEIGHT;
                height += ROW_HEIGHT;
            }
            if (prop.type == NbtType.COMPOUND) {
                for (NbtProperty child : prop.children) {
                    height += ROW_HEIGHT;
                    if (child.type == NbtType.STRING_LIST && child.enabled) {
                        height += child.stringListValues.size() * ROW_HEIGHT;
                        height += ROW_HEIGHT;
                    }
                }
            }
        }
        height += ROW_HEIGHT; // Custom NBT add row
        return height;
    }

    // ==========================================
    // Rendering
    // ==========================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);

        // Header: item display
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            g.renderItem(stack, PADDING, 10);
            g.drawString(this.font, item.getDescription(), PADDING + 22, 14, 0xFFFFFF);
        }
        g.drawString(this.font, itemId, PADDING + 22, 28, 0x888888);
        g.drawString(this.font, "NBT Properties", PADDING, HEADER_HEIGHT - 16, 0xFFCC00);

        // Separator
        g.fill(PADDING, HEADER_HEIGHT - 4, this.width - PADDING, HEADER_HEIGHT - 3, 0x40FFCC00);

        // Content area with scissor
        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 40;
        g.enableScissor(0, listTop, this.width, listBottom);

        int y = listTop - (int) scrollOffset;
        int contentLeft = PADDING;
        int contentRight = this.width - PADDING;

        for (NbtProperty prop : properties) {
            if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                renderProperty(g, prop, contentLeft, y, contentRight, mouseX, mouseY);
            }
            y += ROW_HEIGHT;

            if (prop.type == NbtType.ENCHANTMENT_LIST && prop.enabled) {
                for (int ei = 0; ei < prop.enchantments.size(); ei++) {
                    if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                        renderEnchantmentEntry(g, prop, ei, contentLeft + INDENT, y, contentRight, mouseX, mouseY);
                    }
                    y += ROW_HEIGHT;
                }
                if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                    renderAddButton(g, contentLeft + INDENT, y, "+ Add Enchantment", mouseX, mouseY);
                }
                y += ROW_HEIGHT;
            }

            if (prop.type == NbtType.COMPOUND) {
                for (NbtProperty child : prop.children) {
                    if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                        renderProperty(g, child, contentLeft + INDENT, y, contentRight, mouseX, mouseY);
                    }
                    y += ROW_HEIGHT;

                    if (child.type == NbtType.STRING_LIST && child.enabled) {
                        for (int si = 0; si < child.stringListValues.size(); si++) {
                            if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                                renderStringListEntry(g, child, si, contentLeft + INDENT * 2, y, contentRight, mouseX, mouseY);
                            }
                            y += ROW_HEIGHT;
                        }
                        if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                            renderAddButton(g, contentLeft + INDENT * 2, y, "+ Add Entry", mouseX, mouseY);
                        }
                        y += ROW_HEIGHT;
                    }
                }
            }

            if (prop.type == NbtType.STRING_LIST && prop.enabled && prop.children.isEmpty()) {
                for (int si = 0; si < prop.stringListValues.size(); si++) {
                    if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                        renderStringListEntry(g, prop, si, contentLeft + INDENT, y, contentRight, mouseX, mouseY);
                    }
                    y += ROW_HEIGHT;
                }
                if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                    renderAddButton(g, contentLeft + INDENT, y, "+ Add Entry", mouseX, mouseY);
                }
                y += ROW_HEIGHT;
            }
        }

        // Custom NBT add row
        if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
            renderAddButton(g, contentLeft, y, "+ Custom NBT Key", mouseX, mouseY);
        }

        g.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int barX = this.width - 8;
            int barW = 6;
            int barH = listBottom - listTop;
            int thumbH = Math.max(20, (int) ((float) barH * barH / (barH + maxScroll)));
            int thumbY = listTop + (int) ((float) scrollOffset / maxScroll * (barH - thumbH));
            boolean barHovered = mouseX >= barX && mouseX < barX + barW && mouseY >= listTop && mouseY < listBottom;
            g.fill(barX, listTop, barX + barW, listBottom, 0x20FFFFFF);
            int thumbColor = draggingScrollbar ? 0xFFFFCC00 : (barHovered ? 0xC0FFCC00 : 0x80FFCC00);
            g.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Validation warning overlay — on high Z-level so it covers all text
        if (showingWarnings && !validationWarnings.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);

            g.fill(0, 0, this.width, this.height, 0xFF000000);
            int dlgW = 300;
            int dlgH = 50 + validationWarnings.size() * 12 + 30;
            int dlgX = this.width / 2 - dlgW / 2;
            int dlgY = this.height / 2 - dlgH / 2;
            g.fill(dlgX, dlgY, dlgX + dlgW, dlgY + dlgH, 0xF0181818);
            g.fill(dlgX, dlgY, dlgX + dlgW, dlgY + 2, 0xFFFF6600);

            g.drawString(this.font, "Warnings (save anyway?)", dlgX + 10, dlgY + 8, 0xFFFF6600);
            int wy = dlgY + 24;
            for (String warning : validationWarnings) {
                g.drawString(this.font, "- " + warning, dlgX + 10, wy, 0xFFAAAA);
                wy += 12;
            }

            // Save Anyway button
            int btnY = wy + 6;
            int btnSaveX = this.width / 2 - 70;
            int btnCancelX = this.width / 2 + 10;
            boolean saveHover = mouseX >= btnSaveX && mouseX < btnSaveX + 60 && mouseY >= btnY && mouseY < btnY + 18;
            boolean cancelHover = mouseX >= btnCancelX && mouseX < btnCancelX + 60 && mouseY >= btnY && mouseY < btnY + 18;
            g.fill(btnSaveX, btnY, btnSaveX + 60, btnY + 18, saveHover ? 0x80FF6600 : 0x40FF6600);
            g.drawString(this.font, "Save", btnSaveX + 18, btnY + 5, 0xFFFFFF);
            g.fill(btnCancelX, btnY, btnCancelX + 60, btnY + 18, cancelHover ? 0x80FFFFFF : 0x40FFFFFF);
            g.drawString(this.font, "Cancel", btnCancelX + 12, btnY + 5, 0xFFFFFF);

            g.pose().popPose();
        }
    }

    private void renderProperty(GuiGraphics g, NbtProperty prop, int x, int y, int right, int mx, int my) {
        // Checkbox
        int cbX = x;
        int cbY = y + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
        boolean cbHovered = mx >= cbX && mx < cbX + CHECKBOX_SIZE && my >= cbY && my < cbY + CHECKBOX_SIZE;

        g.fill(cbX, cbY, cbX + CHECKBOX_SIZE, cbY + CHECKBOX_SIZE, cbHovered ? 0x60FFFFFF : 0x40FFFFFF);
        g.fill(cbX + 1, cbY + 1, cbX + CHECKBOX_SIZE - 1, cbY + CHECKBOX_SIZE - 1, 0xE0101010);
        if (prop.enabled) {
            g.fill(cbX + 3, cbY + 3, cbX + CHECKBOX_SIZE - 3, cbY + CHECKBOX_SIZE - 3, 0xFFFFCC00);
        }

        // Label
        int textX = cbX + CHECKBOX_SIZE + 6;
        int textColor = prop.enabled ? 0xFFFFFF : 0x888888;
        g.drawString(this.font, prop.key, textX, y + (ROW_HEIGHT - 8) / 2, textColor);

        // Description
        String desc = prop.description;
        int descX = textX + this.font.width(prop.key) + 10;
        if (descX + this.font.width(desc) < right) {
            g.drawString(this.font, desc, descX, y + (ROW_HEIGHT - 8) / 2, 0x555555);
        }

        // Value field for simple types
        if (prop.enabled && (prop.type == NbtType.INTEGER || prop.type == NbtType.STRING)) {
            int fieldW = Math.min(150, right - descX - 10);
            int fieldX = right - fieldW - 10;
            int fieldY = y + 2;
            boolean fieldHovered = mx >= fieldX && mx < fieldX + fieldW && my >= fieldY && my < fieldY + ROW_HEIGHT - 4;
            g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + ROW_HEIGHT - 4, fieldHovered ? 0x50FFFFFF : 0x40FFFFFF);
            g.fill(fieldX + 1, fieldY + 1, fieldX + fieldW - 1, fieldY + ROW_HEIGHT - 5, 0xE0101010);
            String displayVal = prop.value != null ? prop.value : "";
            if (displayVal.isEmpty()) {
                g.drawString(this.font, "click to edit...", fieldX + 4, fieldY + 4, 0x555555);
            } else {
                g.drawString(this.font, displayVal, fieldX + 4, fieldY + 4, 0xCCCCCC);
            }
        }

        if (prop.enabled && prop.type == NbtType.BOOLEAN) {
            boolean boolVal = "true".equals(prop.value);
            String label = boolVal ? "true" : "false";
            int labelX = right - this.font.width(label) - 16;
            g.drawString(this.font, label, labelX, y + (ROW_HEIGHT - 8) / 2, boolVal ? 0x88FF88 : 0xFF8888);
        }
    }

    private void renderEnchantmentEntry(GuiGraphics g, NbtProperty prop, int idx, int x, int y, int right, int mx, int my) {
        EnchantmentEntry ench = prop.enchantments.get(idx);

        // Remove button [X]
        int removeX = x;
        int removeY = y + (ROW_HEIGHT - 10) / 2;
        boolean removeHovered = mx >= removeX && mx < removeX + 10 && my >= removeY && my < removeY + 10;
        g.fill(removeX, removeY, removeX + 10, removeY + 10, removeHovered ? 0x80FF4444 : 0x40FF4444);
        g.drawString(this.font, "x", removeX + 2, removeY + 1, 0xFFFFFF);

        // ID field
        int idX = x + 16;
        int fieldW = (right - idX - 80) / 2;
        boolean idHovered = mx >= idX && mx < idX + fieldW && my >= y + 2 && my < y + ROW_HEIGHT - 4;
        g.fill(idX, y + 2, idX + fieldW, y + ROW_HEIGHT - 4, idHovered ? 0x50FFFFFF : 0x40FFFFFF);
        g.fill(idX + 1, y + 3, idX + fieldW - 1, y + ROW_HEIGHT - 5, 0xE0101010);
        g.drawString(this.font, ench.id.isEmpty() ? "enchantment id..." : ench.id, idX + 4, y + 6, ench.id.isEmpty() ? 0x555555 : 0xCCCCCC);

        // Level label + field
        int lvlLabelX = idX + fieldW + 8;
        g.drawString(this.font, "lvl:", lvlLabelX, y + 6, 0x888888);
        int lvlFieldX = lvlLabelX + this.font.width("lvl:") + 4;
        int lvlFieldW = 50;
        boolean lvlHovered = mx >= lvlFieldX && mx < lvlFieldX + lvlFieldW && my >= y + 2 && my < y + ROW_HEIGHT - 4;
        g.fill(lvlFieldX, y + 2, lvlFieldX + lvlFieldW, y + ROW_HEIGHT - 4, lvlHovered ? 0x50FFFFFF : 0x40FFFFFF);
        g.fill(lvlFieldX + 1, y + 3, lvlFieldX + lvlFieldW - 1, y + ROW_HEIGHT - 5, 0xE0101010);
        g.drawString(this.font, ench.level.isEmpty() ? "1" : ench.level, lvlFieldX + 4, y + 6, ench.level.isEmpty() ? 0x555555 : 0xCCCCCC);
    }

    private void renderStringListEntry(GuiGraphics g, NbtProperty prop, int idx, int x, int y, int right, int mx, int my) {
        String val = prop.stringListValues.get(idx);

        // Remove button
        int removeX = x;
        int removeY = y + (ROW_HEIGHT - 10) / 2;
        boolean removeHovered = mx >= removeX && mx < removeX + 10 && my >= removeY && my < removeY + 10;
        g.fill(removeX, removeY, removeX + 10, removeY + 10, removeHovered ? 0x80FF4444 : 0x40FF4444);
        g.drawString(this.font, "x", removeX + 2, removeY + 1, 0xFFFFFF);

        // Value field
        int fieldX = x + 16;
        int fieldW = right - fieldX - 10;
        g.fill(fieldX, y + 2, fieldX + fieldW, y + ROW_HEIGHT - 4, 0x40FFFFFF);
        g.fill(fieldX + 1, y + 3, fieldX + fieldW - 1, y + ROW_HEIGHT - 5, 0xE0101010);
        g.drawString(this.font, val.isEmpty() ? "click to edit..." : val, fieldX + 4, y + 6, val.isEmpty() ? 0x555555 : 0xCCCCCC);
    }

    private void renderAddButton(GuiGraphics g, int x, int y, String label, int mx, int my) {
        int w = this.font.width(label) + 12;
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + ROW_HEIGHT;
        g.fill(x, y + 2, x + w, y + ROW_HEIGHT - 2, hovered ? 0x40FFCC00 : 0x20FFFFFF);
        g.drawString(this.font, label, x + 6, y + (ROW_HEIGHT - 8) / 2, hovered ? 0xFFCC00 : 0x888888);
    }

    // ==========================================
    // Click handling
    // ==========================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle warning overlay clicks
        if (showingWarnings && !validationWarnings.isEmpty()) {
            int dlgH = 50 + validationWarnings.size() * 12 + 30;
            int dlgY = this.height / 2 - dlgH / 2;
            int btnY = dlgY + 24 + validationWarnings.size() * 12 + 6;
            int btnSaveX = this.width / 2 - 70;
            int btnCancelX = this.width / 2 + 10;
            if (mouseX >= btnSaveX && mouseX < btnSaveX + 60 && mouseY >= btnY && mouseY < btnY + 18) {
                // Save anyway
                JsonObject nbt = buildNbtJson();
                onSave.accept(nbt.size() > 0 ? nbt : null);
                this.minecraft.setScreen(parent);
                return true;
            }
            if (mouseX >= btnCancelX && mouseX < btnCancelX + 60 && mouseY >= btnY && mouseY < btnY + 18) {
                showingWarnings = false;
                return true;
            }
            return true; // consume all clicks while overlay is shown
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 40;

        // Scrollbar click — start dragging
        if (maxScroll > 0) {
            int barX = this.width - 8;
            int barW = 6;
            if (mouseX >= barX && mouseX < barX + barW && mouseY >= listTop && mouseY < listBottom) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listTop, listBottom);
                return true;
            }
        }

        if (mouseY < listTop || mouseY > listBottom) return false;

        int y = listTop - (int) scrollOffset;
        int contentLeft = PADDING;
        int contentRight = this.width - PADDING;

        for (NbtProperty prop : properties) {
            if (handlePropertyClick(prop, contentLeft, y, contentRight, mouseX, mouseY)) return true;
            y += ROW_HEIGHT;

            if (prop.type == NbtType.ENCHANTMENT_LIST && prop.enabled) {
                for (int ei = 0; ei < prop.enchantments.size(); ei++) {
                    if (handleEnchantmentClick(prop, ei, contentLeft + INDENT, y, contentRight, mouseX, mouseY)) return true;
                    y += ROW_HEIGHT;
                }
                int addW = this.font.width("+ Add Enchantment") + 12;
                if (mouseX >= contentLeft + INDENT && mouseX < contentLeft + INDENT + addW && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    prop.enchantments.add(new EnchantmentEntry("", "1"));
                    updateMaxScroll();
                    return true;
                }
                y += ROW_HEIGHT;
            }

            if (prop.type == NbtType.COMPOUND) {
                for (NbtProperty child : prop.children) {
                    if (handlePropertyClick(child, contentLeft + INDENT, y, contentRight, mouseX, mouseY)) return true;
                    y += ROW_HEIGHT;

                    if (child.type == NbtType.STRING_LIST && child.enabled) {
                        for (int si = 0; si < child.stringListValues.size(); si++) {
                            if (handleStringListClick(child, si, contentLeft + INDENT * 2, y, contentRight, mouseX, mouseY)) return true;
                            y += ROW_HEIGHT;
                        }
                        int addW = this.font.width("+ Add Entry") + 12;
                        if (mouseX >= contentLeft + INDENT * 2 && mouseX < contentLeft + INDENT * 2 + addW && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            child.stringListValues.add("");
                            updateMaxScroll();
                            return true;
                        }
                        y += ROW_HEIGHT;
                    }
                }
            }

            if (prop.type == NbtType.STRING_LIST && prop.enabled && prop.children.isEmpty()) {
                for (int si = 0; si < prop.stringListValues.size(); si++) {
                    if (handleStringListClick(prop, si, contentLeft + INDENT, y, contentRight, mouseX, mouseY)) return true;
                    y += ROW_HEIGHT;
                }
                int addW = this.font.width("+ Add Entry") + 12;
                if (mouseX >= contentLeft + INDENT && mouseX < contentLeft + INDENT + addW && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    prop.stringListValues.add("");
                    updateMaxScroll();
                    return true;
                }
                y += ROW_HEIGHT;
            }
        }

        // Custom NBT add
        int addW = this.font.width("+ Custom NBT Key") + 12;
        if (mouseX >= contentLeft && mouseX < contentLeft + addW && mouseY >= y && mouseY < y + ROW_HEIGHT) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            openCustomNbtDialog();
            return true;
        }

        return false;
    }

    private boolean handlePropertyClick(NbtProperty prop, int x, int y, int right, double mx, double my) {
        int cbX = x;
        int cbY = y + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
        if (mx >= cbX && mx < cbX + CHECKBOX_SIZE && my >= cbY && my < cbY + CHECKBOX_SIZE) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            prop.enabled = !prop.enabled;
            if (!prop.enabled) {
                prop.value = null;
                prop.enchantments.clear();
                prop.stringListValues.clear();
            }
            updateMaxScroll();
            return true;
        }

        if (prop.enabled && (prop.type == NbtType.INTEGER || prop.type == NbtType.STRING)) {
            int fieldW = Math.min(150, right - x - 100);
            int fieldX = right - fieldW - 10;
            int fieldY = y + 2;
            if (mx >= fieldX && mx < fieldX + fieldW && my >= fieldY && my < fieldY + ROW_HEIGHT - 4) {
                openValueEditor(prop);
                return true;
            }
        }

        if (prop.enabled && prop.type == NbtType.BOOLEAN) {
            String label = "true".equals(prop.value) ? "true" : "false";
            int labelX = right - this.font.width(label) - 16;
            if (mx >= labelX && mx < right && my >= y && my < y + ROW_HEIGHT) {
                prop.value = "true".equals(prop.value) ? "false" : "true";
                return true;
            }
        }

        return false;
    }

    private boolean handleEnchantmentClick(NbtProperty prop, int idx, int x, int y, int right, double mx, double my) {
        // Remove button
        int removeX = x;
        int removeY = y + (ROW_HEIGHT - 10) / 2;
        if (mx >= removeX && mx < removeX + 10 && my >= removeY && my < removeY + 10) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            prop.enchantments.remove(idx);
            updateMaxScroll();
            return true;
        }

        EnchantmentEntry ench = prop.enchantments.get(idx);

        // ID field click
        int idX = x + 16;
        int fieldW = (right - idX - 80) / 2;
        if (mx >= idX && mx < idX + fieldW && my >= y + 2 && my < y + ROW_HEIGHT - 4) {
            openSuggestingInput("Enchantment ID", ench.id, getEnchantmentSuggestions(), val -> ench.id = val);
            return true;
        }

        // Level field click
        int lvlLabelX = idX + fieldW + 8;
        int lvlFieldX = lvlLabelX + this.font.width("lvl:") + 4;
        int lvlFieldW = 50;
        if (mx >= lvlFieldX && mx < lvlFieldX + lvlFieldW && my >= y + 2 && my < y + ROW_HEIGHT - 4) {
            openSuggestingInput("Level (or range, e.g. 1-4)", ench.level, Collections.emptyList(), val -> ench.level = val);
            return true;
        }

        return false;
    }

    private boolean handleStringListClick(NbtProperty prop, int idx, int x, int y, int right, double mx, double my) {
        int removeX = x;
        int removeY = y + (ROW_HEIGHT - 10) / 2;
        if (mx >= removeX && mx < removeX + 10 && my >= removeY && my < removeY + 10) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            prop.stringListValues.remove(idx);
            updateMaxScroll();
            return true;
        }

        int fieldX = x + 16;
        if (mx >= fieldX && mx < right - 10 && my >= y + 2 && my < y + ROW_HEIGHT - 4) {
            openSuggestingInput("Value", prop.stringListValues.get(idx), Collections.emptyList(), val -> prop.stringListValues.set(idx, val));
            return true;
        }

        return false;
    }

    // ==========================================
    // Editor dialogs
    // ==========================================

    private void openValueEditor(NbtProperty prop) {
        String title = prop.key;
        List<String> suggestions = Collections.emptyList();
        if (prop.type == NbtType.INTEGER) title += " (number or range, e.g. 42 or 1-4)";
        if ("Potion".equals(prop.key)) suggestions = getPotionSuggestions();
        openSuggestingInput(title, prop.value != null ? prop.value : "", suggestions, val -> prop.value = val);
    }

    private void openSuggestingInput(String title, String currentValue, List<String> suggestions, Consumer<String> onDone) {
        this.minecraft.setScreen(new SuggestingInputScreen(this, title, currentValue, suggestions, onDone));
    }

    private void openCustomNbtDialog() {
        this.minecraft.setScreen(new CustomNbtInputScreen(this, (key, value) -> {
            NbtProperty custom = new NbtProperty(key, NbtType.STRING, "Custom NBT key");
            custom.enabled = true;
            custom.value = value;
            properties.add(properties.size(), custom);
            updateMaxScroll();
        }));
    }

    // ==========================================
    // Suggestions
    // ==========================================

    private static List<String> getEnchantmentSuggestions() {
        if (enchantmentIds == null) {
            enchantmentIds = new ArrayList<>();
            for (ResourceLocation key : ForgeRegistries.ENCHANTMENTS.getKeys()) {
                enchantmentIds.add(key.toString());
            }
            Collections.sort(enchantmentIds);
        }
        return enchantmentIds;
    }

    private static List<String> getPotionSuggestions() {
        if (potionIds == null) {
            potionIds = new ArrayList<>();
            for (ResourceLocation key : ForgeRegistries.POTIONS.getKeys()) {
                potionIds.add(key.toString());
            }
            Collections.sort(potionIds);
        }
        return potionIds;
    }

    // ==========================================
    // Save
    // ==========================================

    private void saveAndClose() {
        validationWarnings = validateNbt();
        if (!validationWarnings.isEmpty() && !showingWarnings) {
            showingWarnings = true;
            return;
        }
        JsonObject nbt = buildNbtJson();
        onSave.accept(nbt.size() > 0 ? nbt : null);
        this.minecraft.setScreen(parent);
    }

    private List<String> validateNbt() {
        List<String> warnings = new ArrayList<>();
        for (NbtProperty prop : properties) {
            if (!prop.enabled) continue;
            if (prop.type == NbtType.ENCHANTMENT_LIST) {
                for (EnchantmentEntry ench : prop.enchantments) {
                    if (ench.id.isEmpty()) continue;
                    ResourceLocation enchRL = ResourceLocation.tryParse(ench.id);
                    Enchantment enchObj = enchRL != null ? ForgeRegistries.ENCHANTMENTS.getValue(enchRL) : null;
                    if (enchObj == null) {
                        warnings.add("Unknown enchantment: " + ench.id);
                    } else {
                        int maxLevel = enchObj.getMaxLevel();
                        if (ench.level.matches("\\d+")) {
                            int lvl = Integer.parseInt(ench.level);
                            if (lvl > maxLevel) {
                                warnings.add(ench.id + " max level is " + maxLevel + ", got " + lvl);
                            }
                        } else if (ench.level.matches("\\d+-\\d+")) {
                            String[] parts = ench.level.split("-");
                            int max = Integer.parseInt(parts[1]);
                            if (max > maxLevel) {
                                warnings.add(ench.id + " max level is " + maxLevel + ", range goes to " + max);
                            }
                        }
                    }
                }
            }
            if ("Potion".equals(prop.key) && prop.value != null && !prop.value.isEmpty()) {
                ResourceLocation potionRL = ResourceLocation.tryParse(prop.value);
                if (potionRL == null || ForgeRegistries.POTIONS.getValue(potionRL) == null) {
                    warnings.add("Unknown potion: " + prop.value);
                }
            }
        }
        return warnings;
    }

    private JsonObject buildNbtJson() {
        JsonObject nbt = new JsonObject();

        for (NbtProperty prop : properties) {
            if (!prop.enabled) continue;

            switch (prop.type) {
                case INTEGER -> {
                    if (prop.value != null && !prop.value.isEmpty()) {
                        if (prop.value.matches("\\d+-\\d+")) {
                            nbt.addProperty(prop.key, prop.value);
                        } else {
                            try {
                                nbt.addProperty(prop.key, Integer.parseInt(prop.value));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                case STRING -> {
                    if (prop.value != null && !prop.value.isEmpty()) {
                        nbt.addProperty(prop.key, prop.value);
                    }
                }
                case BOOLEAN -> nbt.addProperty(prop.key, "true".equals(prop.value));
                case ENCHANTMENT_LIST -> {
                    JsonArray arr = new JsonArray();
                    for (EnchantmentEntry ench : prop.enchantments) {
                        if (!ench.id.isEmpty()) {
                            JsonObject obj = new JsonObject();
                            obj.addProperty("id", ench.id);
                            if (ench.level.matches("\\d+-\\d+")) {
                                obj.addProperty("lvl", ench.level);
                            } else {
                                try {
                                    obj.addProperty("lvl", Integer.parseInt(ench.level));
                                } catch (NumberFormatException e) {
                                    obj.addProperty("lvl", 1);
                                }
                            }
                            arr.add(obj);
                        }
                    }
                    if (arr.size() > 0) nbt.add(prop.key, arr);
                }
                case STRING_LIST -> {
                    JsonArray arr = new JsonArray();
                    for (String val : prop.stringListValues) {
                        if (!val.isEmpty()) arr.add(val);
                    }
                    if (arr.size() > 0) nbt.add(prop.key, arr);
                }
                case COMPOUND -> {
                    JsonObject compound = new JsonObject();
                    for (NbtProperty child : prop.children) {
                        if (!child.enabled) continue;
                        switch (child.type) {
                            case STRING -> {
                                if (child.value != null && !child.value.isEmpty()) {
                                    compound.addProperty(child.key, child.value);
                                }
                            }
                            case STRING_LIST -> {
                                JsonArray arr = new JsonArray();
                                for (String val : child.stringListValues) {
                                    if (!val.isEmpty()) arr.add(val);
                                }
                                if (arr.size() > 0) compound.add(child.key, arr);
                            }
                            default -> {}
                        }
                    }
                    if (compound.size() > 0) nbt.add(prop.key, compound);
                }
            }
        }
        return nbt;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 10));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && maxScroll > 0) {
            updateScrollFromMouse(mouseY, HEADER_HEIGHT, this.height - 40);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
        int barH = listBottom - listTop;
        int thumbH = Math.max(20, (int) ((float) barH * barH / (barH + maxScroll)));
        float usableH = barH - thumbH;
        float relativeY = (float) (mouseY - listTop - thumbH / 2.0) / usableH;
        relativeY = Math.max(0, Math.min(1, relativeY));
        scrollOffset = relativeY * maxScroll;
    }

    @Override
    public boolean isPauseScreen() { return true; }

    // ==========================================
    // Data types
    // ==========================================

    enum NbtType {
        INTEGER, STRING, BOOLEAN, ENCHANTMENT_LIST, STRING_LIST, COMPOUND
    }

    static class NbtProperty {
        String key;
        NbtType type;
        String description;
        boolean enabled = false;
        String value = null;
        List<EnchantmentEntry> enchantments = new ArrayList<>();
        List<String> stringListValues = new ArrayList<>();
        List<NbtProperty> children = new ArrayList<>();

        NbtProperty(String key, NbtType type, String description) {
            this.key = key;
            this.type = type;
            this.description = description;
        }
    }

    static class EnchantmentEntry {
        String id;
        String level;

        EnchantmentEntry(String id, String level) {
            this.id = id;
            this.level = level;
        }
    }

    // ==========================================
    // Input screen with autocomplete suggestions
    // ==========================================

    static class SuggestingInputScreen extends Screen {
        private final Screen parent;
        private final String title;
        private final String currentValue;
        private final List<String> allSuggestions;
        private final Consumer<String> onDone;
        private EditBox inputField;
        private List<String> filteredSuggestions = new ArrayList<>();
        private int suggestionScroll = 0;
        private static final int MAX_VISIBLE_SUGGESTIONS = 6;
        private static final int SUGGESTION_HEIGHT = 14;

        SuggestingInputScreen(Screen parent, String title, String currentValue, List<String> suggestions, Consumer<String> onDone) {
            super(Component.literal(title));
            this.parent = parent;
            this.title = title;
            this.currentValue = currentValue;
            this.allSuggestions = suggestions;
            this.onDone = onDone;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2 - 30;

            inputField = new EditBox(this.font, centerX - 120, centerY, 240, 20, Component.literal(title));
            inputField.setMaxLength(512);
            inputField.setValue(currentValue);
            inputField.setResponder(val -> {
                updateSuggestions(val);
                suggestionScroll = 0;
            });
            this.addRenderableWidget(inputField);
            this.setFocused(inputField);

            this.addRenderableWidget(StyledButton.of(
                    Component.literal("OK"),
                    btn -> confirm(),
                    centerX - 50, centerY + 26 + Math.min(MAX_VISIBLE_SUGGESTIONS, Math.max(0, allSuggestions.size())) * SUGGESTION_HEIGHT + 6, 100, 20));

            updateSuggestions(currentValue);
        }

        private void updateSuggestions(String input) {
            if (allSuggestions.isEmpty() || input.isEmpty()) {
                filteredSuggestions = allSuggestions.isEmpty() ? Collections.emptyList() : new ArrayList<>(allSuggestions);
                return;
            }
            String lower = input.toLowerCase();
            filteredSuggestions = allSuggestions.stream()
                    .filter(s -> s.toLowerCase().contains(lower))
                    .limit(50)
                    .collect(Collectors.toList());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, this.width, this.height, 0xC0000000);
            int centerX = this.width / 2;
            int centerY = this.height / 2 - 30;

            int visibleSuggestions = Math.min(MAX_VISIBLE_SUGGESTIONS, filteredSuggestions.size());
            int suggestionsH = visibleSuggestions * SUGGESTION_HEIGHT;

            // Dialog background
            int dlgW = 280;
            int dlgH = 90 + suggestionsH;
            int dlgX = centerX - dlgW / 2;
            int dlgY = centerY - 20;
            g.fill(dlgX, dlgY, dlgX + dlgW, dlgY + dlgH, 0xF0181818);
            g.fill(dlgX, dlgY, dlgX + dlgW, dlgY + 2, 0xFFFFCC00);

            g.drawString(this.font, title, dlgX + 10, dlgY + 8, 0xFFCC00);

            // Suggestions list
            if (!filteredSuggestions.isEmpty() && !allSuggestions.isEmpty()) {
                int sugY = centerY + 24;
                int sugX = centerX - 120;
                int sugW = 240;

                g.fill(sugX, sugY, sugX + sugW, sugY + suggestionsH, 0xF0222222);

                int maxScroll = Math.max(0, filteredSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
                suggestionScroll = Math.min(suggestionScroll, maxScroll);

                for (int i = 0; i < visibleSuggestions; i++) {
                    int idx = i + suggestionScroll;
                    if (idx >= filteredSuggestions.size()) break;
                    String suggestion = filteredSuggestions.get(idx);
                    int itemY = sugY + i * SUGGESTION_HEIGHT;
                    boolean hovered = mouseX >= sugX && mouseX < sugX + sugW && mouseY >= itemY && mouseY < itemY + SUGGESTION_HEIGHT;

                    if (hovered) {
                        g.fill(sugX, itemY, sugX + sugW, itemY + SUGGESTION_HEIGHT, 0x40FFCC00);
                    }

                    // Highlight matching part
                    String input = inputField.getValue().toLowerCase();
                    int matchIdx = suggestion.toLowerCase().indexOf(input);
                    if (matchIdx >= 0 && !input.isEmpty()) {
                        String before = suggestion.substring(0, matchIdx);
                        String match = suggestion.substring(matchIdx, matchIdx + input.length());
                        String after = suggestion.substring(matchIdx + input.length());
                        int tx = sugX + 4;
                        g.drawString(this.font, before, tx, itemY + 3, 0x999999);
                        tx += this.font.width(before);
                        g.drawString(this.font, match, tx, itemY + 3, 0xFFCC00);
                        tx += this.font.width(match);
                        g.drawString(this.font, after, tx, itemY + 3, 0x999999);
                    } else {
                        g.drawString(this.font, suggestion, sugX + 4, itemY + 3, 0x999999);
                    }
                }

                // Scroll indicator
                if (filteredSuggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
                    int barX = sugX + sugW - 3;
                    int thumbH = Math.max(4, suggestionsH * MAX_VISIBLE_SUGGESTIONS / filteredSuggestions.size());
                    int thumbY = sugY + (int) ((float) suggestionScroll / maxScroll * (suggestionsH - thumbH));
                    g.fill(barX, sugY, barX + 2, sugY + suggestionsH, 0x20FFFFFF);
                    g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0x80FFCC00);
                }
            }

            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;

            // Check if clicking on a suggestion
            if (!filteredSuggestions.isEmpty() && !allSuggestions.isEmpty()) {
                int centerX = this.width / 2;
                int centerY = this.height / 2 - 30;
                int sugY = centerY + 24;
                int sugX = centerX - 120;
                int sugW = 240;
                int visibleSuggestions = Math.min(MAX_VISIBLE_SUGGESTIONS, filteredSuggestions.size());

                if (mouseX >= sugX && mouseX < sugX + sugW && mouseY >= sugY && mouseY < sugY + visibleSuggestions * SUGGESTION_HEIGHT) {
                    int idx = (int) ((mouseY - sugY) / SUGGESTION_HEIGHT) + suggestionScroll;
                    if (idx >= 0 && idx < filteredSuggestions.size()) {
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        inputField.setValue(filteredSuggestions.get(idx));
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!filteredSuggestions.isEmpty()) {
                int maxScroll = Math.max(0, filteredSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
                suggestionScroll = (int) Math.max(0, Math.min(maxScroll, suggestionScroll - delta));
                return true;
            }
            return false;
        }

        private void confirm() {
            onDone.accept(inputField.getValue());
            this.minecraft.setScreen(parent);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 257) { confirm(); return true; } // Enter
            if (keyCode == 256) { this.minecraft.setScreen(parent); return true; } // Escape
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean isPauseScreen() { return true; }
    }

    // ==========================================
    // Custom NBT input screen
    // ==========================================

    static class CustomNbtInputScreen extends Screen {
        private final Screen parent;
        private final java.util.function.BiConsumer<String, String> onDone;
        private EditBox keyField;
        private EditBox valueField;

        CustomNbtInputScreen(Screen parent, java.util.function.BiConsumer<String, String> onDone) {
            super(Component.literal("Custom NBT"));
            this.parent = parent;
            this.onDone = onDone;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            keyField = new EditBox(this.font, centerX - 120, centerY - 24, 240, 20, Component.literal("Key"));
            keyField.setMaxLength(128);
            keyField.setHint(Component.literal("NBT Key..."));
            this.addRenderableWidget(keyField);
            this.setFocused(keyField);

            valueField = new EditBox(this.font, centerX - 120, centerY + 2, 240, 20, Component.literal("Value"));
            valueField.setMaxLength(512);
            valueField.setHint(Component.literal("Value..."));
            this.addRenderableWidget(valueField);

            this.addRenderableWidget(StyledButton.of(
                    Component.literal("OK"),
                    btn -> confirm(),
                    centerX - 50, centerY + 30, 100, 20));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, this.width, this.height, 0xC0000000);
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            int dlgW = 280;
            int dlgH = 110;
            int dlgX = centerX - dlgW / 2;
            int dlgY = centerY - dlgH / 2;
            g.fill(dlgX, dlgY, dlgX + dlgW, dlgY + dlgH, 0xF0181818);
            g.fill(dlgX, dlgY, dlgX + dlgW, dlgY + 2, 0xFFFFCC00);

            g.drawString(this.font, "Custom NBT Key", dlgX + 10, dlgY + 8, 0xFFCC00);

            super.render(g, mouseX, mouseY, partialTick);
        }

        private void confirm() {
            String key = keyField.getValue().trim();
            String value = valueField.getValue().trim();
            if (!key.isEmpty()) {
                onDone.accept(key, value);
            }
            this.minecraft.setScreen(parent);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 257) { confirm(); return true; }
            if (keyCode == 256) { this.minecraft.setScreen(parent); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean isPauseScreen() { return true; }
    }
}
