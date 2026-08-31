package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;
import net.bananemdnsa.historystages.client.editor.nbt.NbtPresets;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The picker behind "add criterion", with one tab per way an item can be matched.
 *
 * <p>The four tabs are not cosmetic groupings — they are the four shapes {@code NbtMatcher}
 * actually distinguishes. Common and Components both write to {@code components}; Enchantments
 * writes the two keys the matcher synthesises; Custom data writes a plain top-level key, which is
 * the only kind that is looked up in {@code custom_data}.
 */
public class SearchableNbtCriterionList extends AbstractSearchableList<SearchableNbtCriterionList.Option> {

    /** Prefixes on {@link Option#selection}, so the screen knows what to build from one string. */
    public static final String PRESET = "preset:";
    public static final String COMPONENT = "component:";
    public static final String ENCHANTMENTS = "enchantments:";
    public static final String CUSTOM_DATA = "customdata:";

    /**
     * @param label     what the row shows
     * @param detail    the technical id, dimmed in the second column; empty when the label is one
     * @param selection prefix plus payload, handed to the select callback
     * @param identity  what this would add, in {@code NbtCriterion.identity()} form
     * @param tooltip   the full text, shown on hover — rows truncate, this does not
     */
    public record Option(String label, String detail, String selection, String identity,
                         String tooltip) {}

    private static final int TAB_COMMON = 0;
    private static final int TAB_COMPONENTS = 1;
    private static final int TAB_ENCHANTMENTS = 2;
    private static final int TAB_CUSTOM_DATA = 3;

    /** Where the dimmed id column starts, as a fraction of the row width. */
    private static final float DETAIL_COLUMN = 0.42f;

    private int activeTab = TAB_COMMON;
    private final EditorTooltip tooltip = new EditorTooltip();
    /** Row under the cursor this frame, handed from {@link #renderRow} to {@link #afterRender}. */
    private Option hoveredEntry;

    public SearchableNbtCriterionList(Consumer<String> onSelect,
                                      Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.nbt.picker.placeholder").getString(),
                onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> ownTabLabels() {
        return List.of(
                Component.translatable("editor.historystages.nbt.tab.common").getString(),
                Component.translatable("editor.historystages.nbt.tab.components").getString(),
                Component.translatable("editor.historystages.nbt.tab.enchantments").getString(),
                Component.translatable("editor.historystages.nbt.tab.custom_data").getString());
    }

    @Override
    protected void onOwnTabChanged(int index) {
        activeTab = index;
        reloadEntries();
    }

    @Override
    protected int getPanelWidth() {
        return 280;
    }

    @Override
    protected List<Option> loadEntries() {
        return switch (activeTab) {
            case TAB_COMPONENTS -> componentOptions();
            case TAB_ENCHANTMENTS -> enchantmentOptions();
            case TAB_CUSTOM_DATA -> List.of(new Option(
                    Component.translatable("editor.historystages.nbt.picker.custom_key").getString(),
                    "", CUSTOM_DATA, "",
                    Component.translatable("editor.historystages.nbt.desc.custom_data").getString()));
            default -> presetOptions();
        };
    }

    private static List<Option> presetOptions() {
        List<Option> options = new ArrayList<>();
        for (NbtPresets.Preset preset : NbtPresets.common()) {
            options.add(new Option(
                    Component.translatable(preset.nameKey()).getString(),
                    preset.componentId(),
                    PRESET + preset.componentId(),
                    "components." + preset.componentId(),
                    Component.translatable(preset.descriptionKey()).getString()
                            + " (" + preset.componentId() + ")"));
        }
        return options;
    }

    /**
     * Every registered component that can actually be matched.
     *
     * <p>Types without a codec are left out on purpose: {@code NbtMatcher.encodeComponent} returns
     * null for them, so a criterion naming one could never match. Offering them here would be a
     * promise the matcher does not keep.
     */
    private static List<Option> componentOptions() {
        List<Option> options = new ArrayList<>();
        for (var entry : BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet()) {
            DataComponentType<?> type = entry.getValue();
            if (type.codec() == null) continue;
            ResourceLocation id = entry.getKey().location();
            options.add(new Option(id.getPath(), id.getNamespace(),
                    COMPONENT + id, "components." + id, id.toString()));
        }
        options.sort((a, b) -> a.selection().compareToIgnoreCase(b.selection()));
        return options;
    }

    private static List<Option> enchantmentOptions() {
        return List.of(enchantmentOption(NbtPresets.ENCHANTMENTS),
                enchantmentOption(NbtPresets.STORED_ENCHANTMENTS));
    }

    /**
     * The two rows carry their friendly name, so the difference between them is readable in the
     * list itself. Side by side under their raw keys they read as the same thing twice.
     */
    private static Option enchantmentOption(String key) {
        return new Option(
                Component.translatable(NbtPresets.enchantmentNameKey(key)).getString(),
                key,
                ENCHANTMENTS + key,
                key,
                Component.translatable(NbtPresets.enchantmentDescriptionKey(key)).getString());
    }

    @Override
    protected String getIdForFilter(Option entry) {
        // The namespace check reads this, so components have to answer with their full id.
        return entry.identity().startsWith("components.")
                ? entry.identity().substring("components.".length())
                : entry.identity();
    }

    @Override
    protected String getIdForAddedCheck(Option entry) {
        return entry.identity();
    }

    @Override
    protected boolean matchesQuery(Option entry, String lowerCaseQuery) {
        return entry.label().toLowerCase().contains(lowerCaseQuery)
                || entry.detail().toLowerCase().contains(lowerCaseQuery)
                || entry.identity().toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(Option entry) {
        return entry.selection();
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, Option entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        // Two fixed columns rather than one left- and one right-aligned run. Right-aligning pushed
        // the ids against the panel edge, where the longest of them lost their tail; from a column
        // they all start at the same x and have the whole remaining width.
        int labelX = x + 3;
        int detailX = x + 3 + Math.round((w - 9) * DETAIL_COLUMN);

        String label = fit(font, entry.label(), detailX - 6 - labelX);
        g.drawString(font, label, labelX, y + 4, hovered ? 0xFFFFFF : 0xBBBBBB, false);

        if (!entry.detail().isEmpty()) {
            String detail = fit(font, entry.detail(), x + w - 6 - detailX);
            g.drawString(font, detail, detailX, y + 4, 0x777777, false);
        }

        if (hovered) hoveredEntry = entry;
    }

    /**
     * Hover tooltip with the untruncated text. The rows have to cut long ids somewhere; this is
     * where the whole thing is readable, on every tab.
     */
    @Override
    protected void afterRender(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Option entry = hoveredEntry;
        hoveredEntry = null;

        var window = Minecraft.getInstance().getWindow();
        tooltip.render(g, font,
                entry == null ? null : entry.selection(),
                entry == null ? null : entry.tooltip(),
                mouseX, mouseY, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    private static String fit(Font font, String text, int room) {
        if (room <= 0) return "";
        if (font.width(text) <= room) return text;
        int ellipsis = font.width("...");
        if (room <= ellipsis) return "";
        return font.plainSubstrByWidth(text, room - ellipsis) + "...";
    }
}
