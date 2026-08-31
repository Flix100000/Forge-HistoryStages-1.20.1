package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of every enchantment the loaded world knows.
 *
 * <p>Enchantments live in a datapack registry, not a built-in one, so this reads the level's
 * registry access rather than {@code BuiltInRegistries} — a pack that adds its own shows up here,
 * and nothing shows up at all outside a world.
 */
public class SearchableEnchantmentList extends AbstractSearchableList<String> {

    public SearchableEnchantmentList(Consumer<String> onSelect,
                                     Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.search.placeholder.enchantments").getString(),
                onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> loadEntries() {
        List<String> ids = new ArrayList<>();
        Registry<Enchantment> registry = registry();
        if (registry != null) {
            for (ResourceLocation key : registry.keySet()) {
                ids.add(key.toString());
            }
            ids.sort(String::compareToIgnoreCase);
        }
        return ids;
    }

    private static Registry<Enchantment> registry() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    }

    /** Localised enchantment name, falling back to the raw id when the world is gone. */
    public static String displayName(String enchantmentId) {
        Registry<Enchantment> registry = registry();
        ResourceLocation key = ResourceLocation.tryParse(enchantmentId);
        if (registry == null || key == null) return enchantmentId;
        Enchantment enchantment = registry.get(key);
        return enchantment == null ? enchantmentId : enchantment.description().getString();
    }

    @Override
    protected String getIdForFilter(String entry) {
        return entry;
    }

    @Override
    protected boolean matchesQuery(String entry, String lowerCaseQuery) {
        return entry.toLowerCase().contains(lowerCaseQuery)
                || displayName(entry).toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(String entry) {
        return entry;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        String name = displayName(entry);
        g.drawString(font, name, x + 3, y + 4, hovered ? 0xFFFFFF : 0xBBBBBB, false);

        int idX = x + 3 + font.width(name) + 6;
        int available = x + w - 6 - idX;
        if (available <= 0) return;

        String id = entry;
        if (font.width(id) > available) {
            id = font.plainSubstrByWidth(id, available - font.width("...")) + "...";
        }
        g.drawString(font, id, idX, y + 4, 0x777777, false);
    }
}
