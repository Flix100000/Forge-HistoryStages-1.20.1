package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.api.editor.widget.SearchBar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Cosmetic chrome shared by the search-panel overlays — the dark frame, the standard
 * filter dropdown, and the namespace / hide-already-added filter logic.
 *
 * <p>Used by widgets that don't fit the {@link AbstractSearchableList} row-list abstraction
 * (item grid, recipe grid, entity preview) but still want consistent panel chrome and
 * filter behaviour.
 */
public final class SearchPanelChrome {

    private SearchPanelChrome() {}

    /**
     * Builds a {@link SearchBar} with the standard filter dropdown: optional "hide already
     * added" toggle, plus the mutually-exclusive "only vanilla" / "only modded" pair.
     */
    public static SearchBar createSearchBar(String placeholder,
                                            Consumer<String> onChange,
                                            Supplier<Collection<String>> hideAddedSupplier) {
        SearchBar bar = new SearchBar(placeholder).onChange(onChange);
        if (hideAddedSupplier != null) {
            bar.filters().addOption("hide_added", "Hide already added", null);
        }
        bar.filters().addOption("only_vanilla", "Only vanilla", "source");
        bar.filters().addOption("only_modded", "Only modded", "source");
        return bar;
    }

    /** Draws the standard outer-border / inner-fill panel frame. */
    public static void renderFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF3D3D3D);
        g.fill(x, y, x + w, y + h, 0xFF1A1A1A);
    }

    /**
     * Draws the standard confirm button — subtle fill that warms towards the accent
     * colour as {@code hoverProgress} goes 0 → 1, with a solid accent underline.
     */
    public static void renderStyledButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                          String text, float hoverProgress) {
        int bgAlpha = (int) (0x30 + hoverProgress * 0x20);
        int bgR = 0xFF;
        int bgG = (int) (0xFF - hoverProgress * 0x33);
        int bgB = (int) (0xFF - hoverProgress * 0xFF);
        g.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB);

        int accentAlpha = (int) (0x60 + hoverProgress * 0x9F);
        g.fill(x, y + h - 2, x + w, y + h, (accentAlpha << 24) | 0xFFCC00);

        g.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        g.fill(x, y, x + 1, y + h, 0x15FFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0x15FFFFFF);

        int textGray = (int) (0xCC + hoverProgress * 0x33);
        int textColor = (0xFF << 24) | (textGray << 16) | (textGray << 8) | textGray;
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, textColor, false);
    }

    /**
     * Returns true if {@code id} passes all of the filters registered by
     * {@link #createSearchBar}: hide-already-added (when active and the supplier contains it)
     * and namespace-based vanilla/modded.
     */
    public static boolean passesDefaultFilters(SearchBar bar, String id,
                                               Supplier<Collection<String>> hideAddedSupplier) {
        if (id == null) return true;
        if (bar.filters().isActive("hide_added") && hideAddedSupplier != null) {
            Collection<String> added = hideAddedSupplier.get();
            if (added != null && added.contains(id)) return false;
        }
        return passesNamespaceFilters(bar, id);
    }

    /**
     * Namespace-only filter check. Use when "hide already added" has custom semantics that
     * cannot be expressed as a simple containment check (e.g. recipe lookup by output).
     */
    public static boolean passesNamespaceFilters(SearchBar bar, String id) {
        if (id == null) return true;
        String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "";
        boolean isVanilla = "minecraft".equals(namespace);
        if (bar.filters().isActive("only_vanilla") && !isVanilla) return false;
        if (bar.filters().isActive("only_modded") && isVanilla) return false;
        return true;
    }
}
