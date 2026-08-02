package net.bananemdnsa.historystages.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputField;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputValues;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;

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
    private final boolean tagMode;
    private final JsonObject currentNbt;
    private final Consumer<JsonObject> onSave;

    // NBT property definitions — built once, persisted across init() calls
    private final List<NbtProperty> properties = new ArrayList<>();
    // Generic data-component entries (rendered in their own section below the data properties)
    private final List<ComponentEntry> componentEntries = new ArrayList<>();
    private boolean propertiesBuilt = false;
    private double scrollOffset = 0;
    /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
    private final Anim smoothScroll = new Anim();
    /** Hover progress of the component rows, keyed by their label. */
    private final java.util.Map<String, Anim> rowHover = new java.util.HashMap<>();
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
    private static List<String> componentIds = null;

    public NbtItemEditScreen(Screen parent, String itemId, JsonObject currentNbt, Consumer<JsonObject> onSave) {
        this(parent, itemId, false, currentNbt, onSave);
    }

    public NbtItemEditScreen(Screen parent, String itemId, boolean tagMode, JsonObject currentNbt, Consumer<JsonObject> onSave) {
        super(Component.translatable("editor.historystages.nbt.title"));
        this.parent = parent;
        this.itemId = itemId;
        this.tagMode = tagMode;
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
                Component.translatable("editor.historystages.nbt.save"),
                btn -> saveNbt(),
                this.width / 2 - 50, this.height - 30, 100, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.nbt.clear_all"),
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
                    componentEntries.clear();
                },
                this.width - PADDING - 80, this.height - 30, 80, 20));

        updateMaxScroll();
    }

    private void buildPropertyTree() {
        properties.clear();

        // Enchantments
        properties.add(new NbtProperty("Enchantments", NbtType.ENCHANTMENT_LIST, Component.translatable("editor.historystages.nbt.desc.enchantments").getString()));

        // StoredEnchantments (enchanted books)
        properties.add(new NbtProperty("StoredEnchantments", NbtType.ENCHANTMENT_LIST, Component.translatable("editor.historystages.nbt.desc.stored_enchantments").getString()));

        // CustomModelData
        properties.add(new NbtProperty("CustomModelData", NbtType.INTEGER, Component.translatable("editor.historystages.nbt.desc.custom_model_data").getString()));

        // display compound
        NbtProperty display = new NbtProperty("display", NbtType.COMPOUND, Component.translatable("editor.historystages.nbt.desc.display").getString());
        display.children.add(new NbtProperty("Name", NbtType.STRING, Component.translatable("editor.historystages.nbt.desc.name").getString()));
        display.children.add(new NbtProperty("Lore", NbtType.STRING_LIST, Component.translatable("editor.historystages.nbt.desc.lore").getString()));
        properties.add(display);

        // Potion
        properties.add(new NbtProperty("Potion", NbtType.STRING, Component.translatable("editor.historystages.nbt.desc.potion").getString()));

        // Unbreakable
        properties.add(new NbtProperty("Unbreakable", NbtType.BOOLEAN, Component.translatable("editor.historystages.nbt.desc.unbreakable").getString()));

        // RepairCost
        properties.add(new NbtProperty("RepairCost", NbtType.INTEGER, Component.translatable("editor.historystages.nbt.desc.repair_cost").getString()));
    }

    private void loadCurrentValues() {
        if (currentNbt == null) return;

        // Collect known property keys
        java.util.Set<String> knownKeys = new java.util.HashSet<>();
        knownKeys.add("components"); // reserved for the components section
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

        // Components section
        if (currentNbt.has("components") && currentNbt.get("components").isJsonObject()) {
            JsonObject components = currentNbt.getAsJsonObject("components");
            for (var entry : components.entrySet()) {
                ComponentEntry ce = new ComponentEntry(entry.getKey());
                ce.valueJson = entry.getValue().toString();
                componentEntries.add(ce);
            }
        }

        // Load unknown keys as custom NBT properties
        for (var entry : currentNbt.entrySet()) {
            if (!knownKeys.contains(entry.getKey())) {
                NbtProperty custom = new NbtProperty(entry.getKey(), NbtType.STRING, Component.translatable("editor.historystages.nbt.desc.custom_key").getString());
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
        int height = ROW_HEIGHT; // "── Data Properties ──" section header
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
        height += ROW_HEIGHT; // "── Components ──" section header
        height += componentEntries.size() * ROW_HEIGHT;
        height += ROW_HEIGHT; // "+ Add Component" row
        return height;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // ==========================================
    // Rendering
    // ==========================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);

        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        // Header: item display (tag mode shows the tag id, no icon)
        if (tagMode) {
            g.drawString(this.font, "#" + itemId, PADDING, 14, 0xFFFFFF);
        } else {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                g.renderItem(stack, PADDING, 10);
                g.drawString(this.font, item.getDescription(), PADDING + 22, 14, 0xFFFFFF);
            }
            g.drawString(this.font, itemId, PADDING + 22, 28, 0x888888);
        }
        g.drawString(this.font, Component.translatable("editor.historystages.nbt.header"), PADDING, HEADER_HEIGHT - 16, 0xFFCC00);

        // Separator
        g.fill(PADDING, HEADER_HEIGHT - 4, this.width - PADDING, HEADER_HEIGHT - 3, 0x40FFCC00);

        // Content area with scissor
        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 40;
        g.enableScissor(0, listTop, this.width, listBottom);

        int y = listTop - Math.round(smoothScroll.value());
        int contentLeft = PADDING;
        int contentRight = this.width - PADDING;

        // Data section header
        if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
            renderSectionDivider(g, Component.translatable("editor.historystages.nbt.section.data").getString(), contentLeft, y, contentRight);
        }
        y += ROW_HEIGHT;

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
                    renderAddButton(g, contentLeft + INDENT, y, Component.translatable("editor.historystages.nbt.add.enchantment").getString(), mouseX, mouseY);
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
                            renderAddButton(g, contentLeft + INDENT * 2, y, Component.translatable("editor.historystages.nbt.add.entry").getString(), mouseX, mouseY);
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
                    renderAddButton(g, contentLeft + INDENT, y, Component.translatable("editor.historystages.nbt.add.entry").getString(), mouseX, mouseY);
                }
                y += ROW_HEIGHT;
            }
        }

        // Custom NBT add row
        if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
            renderAddButton(g, contentLeft, y, Component.translatable("editor.historystages.nbt.add.custom_key").getString(), mouseX, mouseY);
        }
        y += ROW_HEIGHT;

        // Components section header
        if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
            renderSectionDivider(g, Component.translatable("editor.historystages.nbt.section.components").getString(), contentLeft, y, contentRight);
        }
        y += ROW_HEIGHT;

        // Component entries
        for (int ci = 0; ci < componentEntries.size(); ci++) {
            if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
                renderComponentEntry(g, componentEntries.get(ci), contentLeft, y, contentRight, mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }

        // Add Component row
        if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom + ROW_HEIGHT) {
            renderAddButton(g, contentLeft, y, Component.translatable("editor.historystages.nbt.add.component").getString(), mouseX, mouseY);
        }

        g.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int barX = this.width - 8;
            int barW = 6;
            int barH = listBottom - listTop;
            int thumbH = Math.max(20, (int) ((float) barH * barH / (barH + maxScroll)));
            int thumbY = listTop + Math.round(smoothScroll.value() / maxScroll * (barH - thumbH));
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

            g.drawString(this.font, Component.translatable("editor.historystages.nbt.warnings_title"), dlgX + 10, dlgY + 8, 0xFFFF6600);
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
            g.drawString(this.font, Component.translatable("editor.historystages.save"), btnSaveX + 18, btnY + 5, 0xFFFFFF);
            g.fill(btnCancelX, btnY, btnCancelX + 60, btnY + 18, cancelHover ? 0x80FFFFFF : 0x40FFFFFF);
            g.drawString(this.font, Component.translatable("editor.historystages.cancel"), btnCancelX + 12, btnY + 5, 0xFFFFFF);

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
            int borderColor = fieldHovered ? 0xFF6A6A6A : 0xFF4A4A4A;
            g.fill(fieldX - 1, fieldY - 1, fieldX + fieldW + 1, fieldY + ROW_HEIGHT - 3, borderColor);
            g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + ROW_HEIGHT - 4, 0xFF0D0D0D);
            String displayVal = prop.value != null ? prop.value : "";
            if (displayVal.isEmpty()) {
                g.drawString(this.font, Component.translatable("editor.historystages.nbt.click_to_edit"), fieldX + 4, fieldY + 4, 0x555555);
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
        int idBorder = idHovered ? 0xFF6A6A6A : 0xFF4A4A4A;
        g.fill(idX - 1, y + 1, idX + fieldW + 1, y + ROW_HEIGHT - 3, idBorder);
        g.fill(idX, y + 2, idX + fieldW, y + ROW_HEIGHT - 4, 0xFF0D0D0D);
        g.drawString(this.font, ench.id.isEmpty() ? Component.translatable("editor.historystages.nbt.enchantment_id_hint").getString() : ench.id, idX + 4, y + 6, ench.id.isEmpty() ? 0x555555 : 0xCCCCCC);

        // Level label + field
        int lvlLabelX = idX + fieldW + 8;
        String lvlLabel = Component.translatable("editor.historystages.nbt.lvl").getString();
        g.drawString(this.font, lvlLabel, lvlLabelX, y + 6, 0x888888);
        int lvlFieldX = lvlLabelX + this.font.width(lvlLabel) + 4;
        int lvlFieldW = 50;
        boolean lvlHovered = mx >= lvlFieldX && mx < lvlFieldX + lvlFieldW && my >= y + 2 && my < y + ROW_HEIGHT - 4;
        int lvlBorder = lvlHovered ? 0xFF6A6A6A : 0xFF4A4A4A;
        g.fill(lvlFieldX - 1, y + 1, lvlFieldX + lvlFieldW + 1, y + ROW_HEIGHT - 3, lvlBorder);
        g.fill(lvlFieldX, y + 2, lvlFieldX + lvlFieldW, y + ROW_HEIGHT - 4, 0xFF0D0D0D);
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
        boolean fieldHovered = mx >= fieldX && mx < fieldX + fieldW && my >= y + 2 && my < y + ROW_HEIGHT - 4;
        int fieldBorder = fieldHovered ? 0xFF6A6A6A : 0xFF4A4A4A;
        g.fill(fieldX - 1, y + 1, fieldX + fieldW + 1, y + ROW_HEIGHT - 3, fieldBorder);
        g.fill(fieldX, y + 2, fieldX + fieldW, y + ROW_HEIGHT - 4, 0xFF0D0D0D);
        g.drawString(this.font, val.isEmpty() ? Component.translatable("editor.historystages.nbt.click_to_edit").getString() : val, fieldX + 4, y + 6, val.isEmpty() ? 0x555555 : 0xCCCCCC);
    }

    private void renderSectionDivider(GuiGraphics g, String label, int x, int y, int right) {
        int midY = y + ROW_HEIGHT / 2;
        int textW = this.font.width(label);
        int leftLineEnd = x + 12;
        int rightLineStart = x + 12 + 8 + textW + 8;
        g.fill(x, midY, leftLineEnd, midY + 1, 0x60FFCC00);
        g.drawString(this.font, label, leftLineEnd + 8, midY - 4, 0xFFCC00);
        g.fill(rightLineStart, midY, right, midY + 1, 0x60FFCC00);
    }

    private void renderComponentEntry(GuiGraphics g, ComponentEntry comp, int x, int y, int right, int mx, int my) {
        // Remove [x] button on the left
        int removeX = x;
        int removeY = y + (ROW_HEIGHT - 10) / 2;
        boolean removeHovered = mx >= removeX && mx < removeX + 10 && my >= removeY && my < removeY + 10;
        g.fill(removeX, removeY, removeX + 10, removeY + 10, removeHovered ? 0x80FF4444 : 0x40FF4444);
        g.drawString(this.font, "x", removeX + 2, removeY + 1, 0xFFFFFF);

        // "{}" glyph as the visual marker that this is a component, not a top-level key
        int prefixX = removeX + 14;
        g.drawString(this.font, "{}", prefixX, y + (ROW_HEIGHT - 8) / 2, 0xFFCC00);

        // Component ID
        int idX = prefixX + this.font.width("{}") + 6;
        g.drawString(this.font, comp.id != null ? comp.id : "", idX, y + (ROW_HEIGHT - 8) / 2, 0xFFFFFF);

        // Edit button on the right
        String btnLabel = Component.translatable("editor.historystages.nbt.edit_button").getString();
        int btnW = this.font.width(btnLabel) + 14;
        int btnX = right - btnW;
        int btnY = y + 2;
        boolean btnHovered = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + ROW_HEIGHT - 4;
        g.fill(btnX, btnY, btnX + btnW, btnY + ROW_HEIGHT - 4, btnHovered ? 0x40FFCC00 : 0x20FFFFFF);
        g.drawString(this.font, btnLabel, btnX + 7, btnY + 5, btnHovered ? 0xFFCC00 : 0xCCCCCC);

        // Compact JSON preview between ID and edit button, dimmed
        String preview = comp.valueJson != null ? comp.valueJson : "";
        preview = preview.replaceAll("\\s+", " ");
        if (preview.length() > 40) preview = preview.substring(0, 37) + "...";
        int previewX = btnX - this.font.width(preview) - 10;
        int idEndX = idX + this.font.width(comp.id != null ? comp.id : "") + 10;
        if (previewX > idEndX) {
            g.drawString(this.font, preview, previewX, y + (ROW_HEIGHT - 8) / 2, 0x666666);
        }
    }

    private void renderAddButton(GuiGraphics g, int x, int y, String label, int mx, int my) {
        int w = this.font.width(label) + 12;
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + ROW_HEIGHT;
        float hp = Ease.outCubic(rowHover.computeIfAbsent(label, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        g.fill(x, y + 2, x + w, y + ROW_HEIGHT - 2, Fade.mix(0x20FFFFFF, 0x40FFCC00, hp));
        g.drawString(this.font, label, x + 6 + Math.round(hp * 2.0f), y + (ROW_HEIGHT - 8) / 2,
                Fade.mix(0xFF888888, 0xFFFFCC00, hp));
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
                commitNbt();
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

        int y = listTop - Math.round(smoothScroll.value());
        int contentLeft = PADDING;
        int contentRight = this.width - PADDING;

        // Data section header (non-interactive, just consumes a row slot)
        y += ROW_HEIGHT;

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
        y += ROW_HEIGHT;

        // Components section header (non-interactive)
        y += ROW_HEIGHT;

        // Component entries
        for (int ci = 0; ci < componentEntries.size(); ci++) {
            if (handleComponentEntryClick(ci, contentLeft, y, contentRight, mouseX, mouseY)) return true;
            y += ROW_HEIGHT;
        }

        // + Add Component
        int addCompW = this.font.width("+ Add Component") + 12;
        if (mouseX >= contentLeft && mouseX < contentLeft + addCompW && mouseY >= y && mouseY < y + ROW_HEIGHT) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            openAddComponentDialog();
            return true;
        }

        return false;
    }

    private boolean handleComponentEntryClick(int idx, int x, int y, int right, double mx, double my) {
        ComponentEntry comp = componentEntries.get(idx);

        // Remove [x] click
        int removeX = x;
        int removeY = y + (ROW_HEIGHT - 10) / 2;
        if (mx >= removeX && mx < removeX + 10 && my >= removeY && my < removeY + 10) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            componentEntries.remove(idx);
            updateMaxScroll();
            return true;
        }

        // Edit ▸ button click
        String btnLabel = Component.translatable("editor.historystages.nbt.edit_button").getString();
        int btnW = this.font.width(btnLabel) + 14;
        int btnX = right - btnW;
        int btnY = y + 2;
        if (mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + ROW_HEIGHT - 4) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            openComponentValueEditor(comp);
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
            openSuggestingInput(Component.translatable("editor.historystages.nbt.input.enchantment_id").getString(), ench.id, getEnchantmentSuggestions(), val -> ench.id = val);
            return true;
        }

        // Level field click
        int lvlLabelX = idX + fieldW + 8;
        int lvlFieldX = lvlLabelX + this.font.width(Component.translatable("editor.historystages.nbt.lvl").getString()) + 4;
        int lvlFieldW = 50;
        if (mx >= lvlFieldX && mx < lvlFieldX + lvlFieldW && my >= y + 2 && my < y + ROW_HEIGHT - 4) {
            openSuggestingInput(Component.translatable("editor.historystages.nbt.input.level_range").getString(), ench.level, Collections.emptyList(), val -> ench.level = val);
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
            openSuggestingInput(Component.translatable("editor.historystages.nbt.input.value").getString(), prop.stringListValues.get(idx), Collections.emptyList(), val -> prop.stringListValues.set(idx, val));
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
        if (prop.type == NbtType.INTEGER) title += Component.translatable("editor.historystages.nbt.input.number_range").getString();
        if ("Potion".equals(prop.key)) suggestions = getPotionSuggestions();
        openSuggestingInput(title, prop.value != null ? prop.value : "", suggestions, val -> prop.value = val);
    }

    private void openSuggestingInput(String title, String currentValue, List<String> suggestions, Consumer<String> onDone) {
        this.minecraft.setScreen(new SuggestingInputScreen(this, title, currentValue, suggestions, onDone));
    }

    private void openCustomNbtDialog() {
        this.minecraft.setScreen(new CustomNbtInputScreen(this, (key, value) -> {
            NbtProperty custom = new NbtProperty(key, NbtType.STRING, Component.translatable("editor.historystages.nbt.desc.custom_key").getString());
            custom.enabled = true;
            custom.value = value;
            properties.add(properties.size(), custom);
            updateMaxScroll();
        }));
    }

    private void openAddComponentDialog() {
        openSuggestingInput(Component.translatable("editor.historystages.nbt.input.component_id").getString(), "", getComponentSuggestions(), id -> {
            String trimmed = id == null ? "" : id.trim();
            if (trimmed.isEmpty()) return;
            ComponentEntry ce = new ComponentEntry(trimmed);
            componentEntries.add(ce);
            updateMaxScroll();
            // Immediately open the JSON value editor for the new component
            openComponentValueEditor(ce);
        });
    }

    private void openComponentValueEditor(ComponentEntry comp) {
        this.minecraft.setScreen(new ComponentValueEditScreen(this, comp.id, comp.valueJson, json -> {
            comp.valueJson = json;
        }));
    }

    // ==========================================
    // Suggestions
    // ==========================================

    private static List<String> getEnchantmentSuggestions() {
        if (enchantmentIds == null || enchantmentIds.isEmpty()) {
            enchantmentIds = new ArrayList<>();
            if (Minecraft.getInstance().level != null) {
                Registry<Enchantment> reg = Minecraft.getInstance().level.registryAccess()
                        .registryOrThrow(Registries.ENCHANTMENT);
                for (ResourceLocation key : reg.keySet()) {
                    enchantmentIds.add(key.toString());
                }
                Collections.sort(enchantmentIds);
            }
        }
        return enchantmentIds;
    }

    private static List<String> getPotionSuggestions() {
        if (potionIds == null || potionIds.isEmpty()) {
            potionIds = new ArrayList<>();
            for (ResourceLocation key : BuiltInRegistries.POTION.keySet()) {
                potionIds.add(key.toString());
            }
            Collections.sort(potionIds);
        }
        return potionIds;
    }

    private static List<String> getComponentSuggestions() {
        if (componentIds == null || componentIds.isEmpty()) {
            componentIds = new ArrayList<>();
            for (ResourceLocation key : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()) {
                componentIds.add(key.toString());
            }
            Collections.sort(componentIds);
        }
        return componentIds;
    }

    // ==========================================
    // Save
    // ==========================================

    private void saveNbt() {
        validationWarnings = validateNbt();
        if (!validationWarnings.isEmpty() && !showingWarnings) {
            showingWarnings = true;
            return;
        }
        commitNbt();
    }

    /**
     * Hands the NBT up and persists it, staying on this screen. Also clears the warning
     * overlay — without a screen change it would otherwise stay up after saving.
     */
    private void commitNbt() {
        JsonObject nbt = buildNbtJson();
        onSave.accept(nbt.size() > 0 ? nbt : null);
        showingWarnings = false;
    }

    private List<String> validateNbt() {
        List<String> warnings = new ArrayList<>();
        for (NbtProperty prop : properties) {
            if (!prop.enabled) continue;
            if (prop.type == NbtType.ENCHANTMENT_LIST) {
                for (EnchantmentEntry ench : prop.enchantments) {
                    if (ench.id.isEmpty()) continue;
                    ResourceLocation enchRL = ResourceLocation.tryParse(ench.id);
                    Enchantment enchObj = null;
                    if (enchRL != null && Minecraft.getInstance().level != null) {
                        enchObj = Minecraft.getInstance().level.registryAccess()
                                .registryOrThrow(Registries.ENCHANTMENT).get(enchRL);
                    }
                    if (enchObj == null) {
                        warnings.add(Component.translatable("editor.historystages.nbt.warn.unknown_enchantment", ench.id).getString());
                    } else {
                        int maxLevel = enchObj.getMaxLevel();
                        if (ench.level.matches("\\d+")) {
                            int lvl = Integer.parseInt(ench.level);
                            if (lvl > maxLevel) {
                                warnings.add(Component.translatable("editor.historystages.nbt.warn.max_level", ench.id, maxLevel, lvl).getString());
                            }
                        } else if (ench.level.matches("\\d+-\\d+")) {
                            String[] parts = ench.level.split("-");
                            int max = Integer.parseInt(parts[1]);
                            if (max > maxLevel) {
                                warnings.add(Component.translatable("editor.historystages.nbt.warn.max_level_range", ench.id, maxLevel, max).getString());
                            }
                        }
                    }
                }
            }
            if ("Potion".equals(prop.key) && prop.value != null && !prop.value.isEmpty()) {
                ResourceLocation potionRL = ResourceLocation.tryParse(prop.value);
                if (potionRL == null || BuiltInRegistries.POTION.get(potionRL) == null) {
                    warnings.add(Component.translatable("editor.historystages.nbt.warn.unknown_potion", prop.value).getString());
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

        // Components section — emit as a "components" object only if user added any
        if (!componentEntries.isEmpty()) {
            JsonObject components = new JsonObject();
            for (ComponentEntry ce : componentEntries) {
                if (ce.id == null || ce.id.isEmpty()) continue;
                com.google.gson.JsonElement parsed = parseComponentValueOrNull(ce.valueJson);
                if (parsed != null) components.add(ce.id, parsed);
            }
            if (components.size() > 0) nbt.add("components", components);
        }
        return nbt;
    }

    /**
     * Parses a component value into any JSON type (object, array, string,
     * number, boolean). Mod-defined components don't always serialize to an
     * object — e.g. {@code irons_jewelry:stored_pattern} is a bare string — so
     * we accept whatever valid JSON the user provided. Returns null only for
     * empty/invalid/null input.
     */
    private static com.google.gson.JsonElement parseComponentValueOrNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(trimmed);
            return parsed.isJsonNull() ? null : parsed;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 10));
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

    static class ComponentEntry {
        String id;
        String valueJson;

        ComponentEntry(String id) {
            this.id = id;
            this.valueJson = "{}";
        }
    }

    // ==========================================
    // Input screen with autocomplete suggestions
    // ==========================================

    static class SuggestingInputScreen extends AbstractInputScreen {
        private static final int MAX_VISIBLE_SUGGESTIONS = 6;
        private static final int SUGGESTION_HEIGHT = 14;

        private final Screen parent;
        private final String currentValue;
        private final List<String> allSuggestions;
        private final Consumer<String> onDone;
        private List<String> filteredSuggestions = new ArrayList<>();
        private int suggestionScroll = 0;
        /** Hover progress of the suggestion rows, keyed by visible slot. */
        private final java.util.Map<Integer, Anim> suggestionHover = new java.util.HashMap<>();
        private boolean draggingScrollbar = false;
        /** Last query the list was filtered against, so a re-filter can tell a change from a repoll. */
        private String lastFilterInput = null;

        /** List bounds, recomputed every frame from the area the base class hands us. */
        private int listX, listY, listW;

        /** Distance of the scrollbar's left edge from the list's right edge. */
        private static final int SCROLLBAR_INSET_X = 3;

        SuggestingInputScreen(Screen parent, String title, String currentValue, List<String> suggestions, Consumer<String> onDone) {
            super(parent, Component.literal(title));
            this.parent = parent;
            this.currentValue = currentValue;
            this.allSuggestions = suggestions;
            this.onDone = onDone;
            updateSuggestions(currentValue);
        }

        @Override
        protected Component confirmLabel() {
            return Component.translatable("editor.historystages.nbt.ok");
        }

        @Override
        protected List<InputField> fields() {
            return List.of(InputField.text("value").maxLength(512).initial(currentValue));
        }

        /**
         * Reserves the maximum list height rather than the current one: the box geometry is
         * computed once in init(), so a height that tracked the hit count would make the
         * dialog jump on every keystroke.
         */
        @Override
        protected int extraContentHeight() {
            return allSuggestions.isEmpty() ? 0 : MAX_VISIBLE_SUGGESTIONS * SUGGESTION_HEIGHT;
        }

        /**
         * Re-filters the list, and rewinds the scroll whenever the query actually changed —
         * the base class owns the EditBox responder, so the query is polled each frame rather
         * than pushed, and only a real change should move the user's scroll position.
         */
        private void updateSuggestions(String input) {
            boolean queryChanged = !input.equals(lastFilterInput);
            lastFilterInput = input;
            if (queryChanged) suggestionScroll = 0;

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

        private int maxScroll() {
            return Math.max(0, filteredSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
        }

        /** The 5px-wide strip the scrollbar reacts in — wider than the 2px it draws, to be hittable. */
        private boolean inScrollbar(double mx, double my) {
            if (maxScroll() <= 0) return false;
            int barX = listX + listW - SCROLLBAR_INSET_X;
            int listH = visibleCount() * SUGGESTION_HEIGHT;
            return mx >= barX - 2 && mx <= barX + 4 && my >= listY && my < listY + listH;
        }

        /** Maps a y inside the bar onto a scroll row, centring the thumb on the cursor. */
        private void scrollFromMouse(double my) {
            int listH = visibleCount() * SUGGESTION_HEIGHT;
            int thumbH = Math.max(4, listH * MAX_VISIBLE_SUGGESTIONS / Math.max(1, filteredSuggestions.size()));
            float usable = listH - thumbH;
            if (usable <= 0) return;
            float ratio = (float) ((my - listY - thumbH / 2.0) / usable);
            ratio = Math.max(0.0f, Math.min(1.0f, ratio));
            suggestionScroll = Math.round(ratio * maxScroll());
        }

        private int visibleCount() {
            return Math.min(MAX_VISIBLE_SUGGESTIONS, filteredSuggestions.size());
        }

        @Override
        protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            // The base class owns the EditBox responder (it drives validation), so the filter is
            // refreshed from the live value each frame instead of on a change callback.
            updateSuggestions(box(0).getValue());
            suggestionScroll = Math.min(suggestionScroll, maxScroll());

            listX = x;
            listY = y;
            listW = w;

            if (filteredSuggestions.isEmpty()) return;

            int visible = visibleCount();
            int listH = visible * SUGGESTION_HEIGHT;
            g.fill(listX, listY, listX + listW, listY + listH, 0xF0222222);

            String input = box(0).getValue().toLowerCase();
            for (int i = 0; i < visible; i++) {
                int idx = i + suggestionScroll;
                if (idx >= filteredSuggestions.size()) break;
                String suggestion = filteredSuggestions.get(idx);
                int itemY = listY + i * SUGGESTION_HEIGHT;
                boolean hovered = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= itemY && mouseY < itemY + SUGGESTION_HEIGHT;

                float sh = Ease.outCubic(suggestionHover.computeIfAbsent(i, k -> new Anim())
                        .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
                if (sh > 0.001f) {
                    g.fill(listX, itemY, listX + listW, itemY + SUGGESTION_HEIGHT,
                            Fade.rgba(0xFFCC00, 0.25f * sh));
                    g.fill(listX, itemY, listX + 1, itemY + SUGGESTION_HEIGHT, Fade.rgba(0xFFCC00, sh));
                }

                // Highlight the part matching the current input
                int matchIdx = suggestion.toLowerCase().indexOf(input);
                if (matchIdx >= 0 && !input.isEmpty()) {
                    String before = suggestion.substring(0, matchIdx);
                    String match = suggestion.substring(matchIdx, matchIdx + input.length());
                    String after = suggestion.substring(matchIdx + input.length());
                    int tx = listX + 4;
                    g.drawString(this.font, before, tx, itemY + 3, 0x999999);
                    tx += this.font.width(before);
                    g.drawString(this.font, match, tx, itemY + 3, 0xFFCC00);
                    tx += this.font.width(match);
                    g.drawString(this.font, after, tx, itemY + 3, 0x999999);
                } else {
                    g.drawString(this.font, suggestion, listX + 4, itemY + 3, 0x999999);
                }
            }

            // Scrollbar — draggable, and brightening on hover so that reads as an affordance.
            if (filteredSuggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
                int barX = listX + listW - SCROLLBAR_INSET_X;
                int thumbH = Math.max(4, listH * MAX_VISIBLE_SUGGESTIONS / filteredSuggestions.size());
                int thumbY = listY + (int) ((float) suggestionScroll / maxScroll() * (listH - thumbH));
                int thumbColor = draggingScrollbar ? 0xFFFFCC00
                        : (inScrollbar(mouseX, mouseY) ? 0xC0FFCC00 : 0x80FFCC00);
                g.fill(barX, listY, barX + 2, listY + listH, 0x20FFFFFF);
                g.fill(barX, thumbY, barX + 2, thumbY + thumbH, thumbColor);
            }
        }

        @Override
        protected boolean extraContentMouseClicked(double mx, double my, int button) {
            if (button != 0) return false;
            if (filteredSuggestions.isEmpty()) return false;

            // Scrollbar wins over the rows it sits on top of.
            if (inScrollbar(mx, my)) {
                draggingScrollbar = true;
                scrollFromMouse(my);
                return true;
            }

            int listH = visibleCount() * SUGGESTION_HEIGHT;
            if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return false;

            int idx = (int) ((my - listY) / SUGGESTION_HEIGHT) + suggestionScroll;
            if (idx < 0 || idx >= filteredSuggestions.size()) return false;

            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            box(0).setValue(filteredSuggestions.get(idx));
            return true;
        }

        @Override
        protected boolean extraContentMouseDragged(double mx, double my, int button) {
            if (!draggingScrollbar) return false;
            scrollFromMouse(my);
            return true;
        }

        @Override
        protected boolean extraContentMouseReleased(double mx, double my, int button) {
            if (!draggingScrollbar) return false;
            draggingScrollbar = false;
            return true;
        }

        @Override
        protected boolean extraContentMouseScrolled(double mx, double my, double scrollY) {
            if (filteredSuggestions.isEmpty()) return false;
            suggestionScroll = (int) Math.max(0, Math.min(maxScroll(), suggestionScroll - scrollY));
            return true;
        }

        @Override
        protected void onConfirm(InputValues values) {
            onDone.accept(values.getString("value"));
            this.minecraft.setScreen(parent);
        }
    }

    // ==========================================
    // Custom NBT input screen
    // ==========================================

    static class CustomNbtInputScreen extends AbstractInputScreen {
        private final Screen parent;
        private final java.util.function.BiConsumer<String, String> onDone;

        CustomNbtInputScreen(Screen parent, java.util.function.BiConsumer<String, String> onDone) {
            super(parent, Component.translatable("editor.historystages.nbt.custom.heading"));
            this.parent = parent;
            this.onDone = onDone;
        }

        @Override
        protected Component confirmLabel() {
            return Component.translatable("editor.historystages.nbt.ok");
        }

        @Override
        protected List<InputField> fields() {
            return List.of(
                    InputField.text("key")
                            .label(Component.translatable("editor.historystages.nbt.custom.key_label"))
                            .hint(Component.translatable("editor.historystages.nbt.custom.key_hint"))
                            .maxLength(128)
                            .validator(v -> v.isEmpty()
                                    ? Component.translatable("editor.historystages.input.empty") : null),
                    InputField.text("value")
                            .label(Component.translatable("editor.historystages.nbt.custom.value_label"))
                            .hint(Component.translatable("editor.historystages.nbt.custom.value_hint"))
                            .maxLength(512));
        }

        @Override
        protected void onConfirm(InputValues values) {
            onDone.accept(values.getString("key"), values.getString("value"));
            this.minecraft.setScreen(parent);
        }
    }

    // ==========================================
    // Component value editor (raw JSON object for one data-component)
    // ==========================================

    static class ComponentValueEditScreen extends AbstractInputScreen {
        private final Screen parent;
        private final String componentId;
        private final String initialValue;
        private final Consumer<String> onDone;

        ComponentValueEditScreen(Screen parent, String componentId, String initialValue, Consumer<String> onDone) {
            super(parent, Component.translatable("editor.historystages.nbt.component.heading"));
            this.parent = parent;
            this.componentId = componentId;
            this.initialValue = initialValue != null ? initialValue : "{}";
            this.onDone = onDone;
        }

        @Override
        protected Component subtitle() {
            return Component.literal(componentId);
        }

        @Override
        protected int dialogWidth() { return 460; }

        @Override
        protected Component confirmLabel() {
            return Component.translatable("editor.historystages.nbt.ok");
        }

        @Override
        protected List<InputField> fields() {
            return List.of(InputField.text("json")
                    .maxLength(8192)
                    .hint(Component.literal("{\"key\": \"value\"}"))
                    .initial(initialValue)
                    .validator(this::validateJson));
        }

        /** Any JSON type is allowed — objects, arrays, strings, numbers, booleans — but not null. */
        private Component validateJson(String raw) {
            if (raw.isEmpty()) return Component.translatable("editor.historystages.nbt.json.empty");
            try {
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(raw);
                if (parsed.isJsonNull()) {
                    return Component.translatable("editor.historystages.nbt.json.not_object");
                }
                return null;
            } catch (Exception e) {
                return Component.translatable("editor.historystages.nbt.json.invalid", e.getMessage());
            }
        }

        @Override
        protected void onConfirm(InputValues values) {
            onDone.accept(values.getString("json"));
            this.minecraft.setScreen(parent);
        }
    }
}
