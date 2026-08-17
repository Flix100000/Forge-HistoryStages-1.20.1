package net.bananemdnsa.historystages.client.editor.widget.list;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of every registered mob effect.
 *
 * <p>Rows show the localised effect name with the raw ID next to it, so a pack author can find
 * "Blindness" by name but still sees which mod it came from.
 */
public class SearchableEffectList extends AbstractSearchableList<String> {

    public SearchableEffectList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableEffectList(Consumer<String> onSelect, Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.search.placeholder.effects").getString(),
                onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> loadEntries() {
        List<String> effects = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.MOB_EFFECT.keySet()) {
            effects.add(key.toString());
        }
        effects.sort(String::compareToIgnoreCase);
        return effects;
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

    /** Localised effect name, falling back to the raw ID for effects without a translation. */
    public static String displayName(String effectId) {
        ResourceLocation rl = ResourceLocation.tryParse(effectId);
        if (rl == null) return effectId;
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(rl);
        if (effect == null) return effectId;
        return effect.getDisplayName().getString();
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        String name = displayName(entry);
        g.drawString(font, name, x + 3, y + 4, hovered ? 0xFFFFFF : 0xBBBBBB, false);

        int nameW = font.width(name);
        int idX = x + 3 + nameW + 6;
        int avail = w - (idX - x) - 6;
        if (avail <= 0) return;
        String id = entry;
        if (font.width(id) > avail) {
            id = font.plainSubstrByWidth(id, avail - 6) + "...";
        }
        g.drawString(font, id, idX, y + 4, 0x777777, false);
    }
}
