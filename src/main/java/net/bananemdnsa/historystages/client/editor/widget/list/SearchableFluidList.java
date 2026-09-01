package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable overlay list of every fluid that can meaningfully be gated.
 *
 * <p>Reads the built-in fluid registry rather than a datapack one, so unlike the biome list it
 * is populated outside a world too.
 *
 * <p>No criteria tab and no Ctrl-add: a fluid entry carries no NBT, so there is nothing to
 * derive from a held stack.
 */
public class SearchableFluidList extends AbstractSearchableList<String> {

    public SearchableFluidList(Consumer<String> onSelect) {
        this(onSelect, null);
    }

    public SearchableFluidList(Consumer<String> onSelect,
                               Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.search.placeholder.fluids").getString(),
                onSelect, alreadyAddedSupplier);
    }

    @Override
    protected List<String> loadEntries() {
        List<String> entries = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.FLUID.keySet()) {
            Fluid fluid = BuiltInRegistries.FLUID.get(key);
            if (!isOfferable(fluid)) continue;
            entries.add(key.toString());
        }
        entries.sort(String::compareToIgnoreCase);
        return entries;
    }

    /**
     * Source fluids only.
     *
     * <p>The registry holds a flowing twin beside every fluid — {@code minecraft:flowing_lava}
     * next to {@code minecraft:lava} — and they are the same substance. Offering both would put
     * two rows in the picker that mean one thing, and a pack author who gated only one of them
     * would get a lock that works in some places and not others. Gating the source is enough:
     * a container always reports the source fluid, and the pickup handler traces with
     * {@code SOURCE_ONLY}.
     *
     * <p>{@code minecraft:empty} falls out through the same test — it has no source state.
     */
    private static boolean isOfferable(Fluid fluid) {
        return fluid != null && fluid.isSource(fluid.defaultFluidState());
    }

    @Override
    protected String getIdForFilter(String entry) {
        return entry;
    }

    @Override
    protected boolean matchesQuery(String entry, String lowerCaseQuery) {
        return entry.toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(String entry) {
        return entry;
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        drawRowText(g, font, entry, x, y, w, hovered);
    }
}
