package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.api.editor.CategoryTab;
import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Several categories shown as one tab, with a bar of sections to switch between them.
 *
 * <p>Merchant offers are gated by item, by profession and by merchant level. Those are three
 * separate questions holding three different kinds of thing, so they are three lock categories —
 * but they are one decision a pack author makes in one sitting, and three entries in a strip that
 * already holds a dozen would bury the other nine. One tab, three sections.
 *
 * <p><strong>Loading and storing go to every section, not just the visible one.</strong> The
 * sections are only a way of looking at the stage; all three are always live. Storing only what
 * was on screen would drop the professions someone entered before switching to levels, and they
 * would find out when the stage came back without them.
 *
 * <p>The bar itself is not drawn here. It is fixed above the scrolling list rather than sitting
 * at the top of it, so it does not scroll away once a section holds more entries than fit — and
 * that is the host's strip, not the tab's content. What this class owns is which section is
 * chosen and what the sections are called.
 */
public final class CompositeCategoryTab implements CategoryTab {

    /** One section: the tab that edits it, and the lang key naming it on the bar. */
    public record Section(CategoryTab tab, String labelLangKey) {
    }

    private final String categoryId;
    private final String tabLangKey;
    private final String tooltipLangKey;
    private final List<Section> sections;
    private final StageScope scope;

    private int active;

    public CompositeCategoryTab(String categoryId, String tabLangKey, String tooltipLangKey,
                                List<Section> sections, StageScope scope) {
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("a composite tab with no sections has nothing to show");
        }
        this.categoryId = categoryId;
        this.tabLangKey = tabLangKey;
        this.tooltipLangKey = tooltipLangKey;
        this.sections = List.copyOf(sections);
        this.scope = scope;
        this.active = firstEnabledSection();
    }

    /**
     * The section on screen.
     *
     * <p>This is what a host asks when it needs to know what a row <em>means</em>, as opposed to
     * which tab is open. All three sections answer to the same tab, and two of them share its
     * label, so a question about the category alone cannot tell an item row from a profession row
     * — and would offer the NBT editor on both.
     */
    public CategoryTab activeSection() {
        return sections.get(active).tab();
    }

    /**
     * Whether this section can be edited in the scope the tab was built for.
     *
     * <p>Read from the section itself rather than passed in. A category already states which
     * scopes it serves, and copying that statement into the editor is how the two drift apart
     * the next time a category changes its mind.
     */
    public boolean sectionEnabled(int index) {
        if (index < 0 || index >= sections.size()) return false;
        if (scope != StageScope.INDIVIDUAL) return true;
        return sections.get(index).tab().availableForIndividualStages();
    }

    /**
     * The section the tab opens on: the first that can be edited here.
     *
     * <p>Falls back to zero when none can, which only happens for a tab that is not offered at
     * all — {@link #availableForIndividualStages} is false in exactly that case.
     */
    private int firstEnabledSection() {
        for (int i = 0; i < sections.size(); i++) {
            if (sectionEnabled(i)) return i;
        }
        return 0;
    }

    /** The section names, in bar order. */
    public List<String> sectionLabels() {
        List<String> labels = new ArrayList<>(sections.size());
        for (Section section : sections) {
            labels.add(Component.translatable(section.labelLangKey()).getString());
        }
        return labels;
    }

    public int activeIndex() {
        return active;
    }

    /** @return true if the section actually changed, so the host can reset its row animation */
    public boolean setActiveIndex(int index) {
        if (index < 0 || index >= sections.size() || index == active) return false;
        if (!sectionEnabled(index)) return false;
        active = index;
        activeSection().onShown();
        return true;
    }

    /**
     * The tab's own id, not the visible section's.
     *
     * <p>Label, tooltip and "which tab is this" all read this, and those are properties of the tab
     * as a whole. {@link #activeSection} is the finer question, and the host asks it by name.
     */
    @Override
    public String categoryId() {
        return categoryId;
    }

    @Override
    public String tabLangKey() {
        return tabLangKey;
    }

    @Override
    public String tooltipLangKey() {
        return tooltipLangKey;
    }

    /**
     * Offered per player as soon as one section works there.
     *
     * <p>The sections that do not are greyed on the bar rather than taken down with it. Refusing
     * the tab outright would cost the ones that work — and there is no other way to reach them.
     */
    @Override
    public boolean availableForIndividualStages() {
        for (Section section : sections) {
            if (section.tab().availableForIndividualStages()) return true;
        }
        return false;
    }

    @Override
    public List<String> entries() {
        return activeSection().entries();
    }

    /**
     * Every section's rows counted together — what the strip puts in brackets after the name.
     *
     * <p>{@link #entries} answers about the section on screen, because that is what the list
     * draws and what a row index means. The number beside the tab name is about the tab.
     */
    public int totalEntryCount() {
        int total = 0;
        for (Section section : sections) total += section.tab().entries().size();
        return total;
    }

    @Override
    public void removeAt(int index) {
        activeSection().removeAt(index);
    }

    @Override
    @Nullable
    public String iconItemId(int index) {
        return activeSection().iconItemId(index);
    }

    @Override
    @Nullable
    public String badgeText(int index) {
        return activeSection().badgeText(index);
    }

    @Override
    public boolean hasAddButton() {
        return activeSection().hasAddButton();
    }

    @Override
    public int contentHeight(int width) {
        return activeSection().contentHeight(width);
    }

    @Override
    public boolean renderContent(TabRenderContext ctx) {
        return activeSection().renderContent(ctx);
    }

    @Override
    public boolean mouseClicked(TabInputContext ctx, int button) {
        return activeSection().mouseClicked(ctx, button);
    }

    @Override
    public void onShown() {
        activeSection().onShown();
    }

    @Override
    public int rowAt(TabInputContext ctx) {
        return activeSection().rowAt(ctx);
    }

    @Override
    public boolean mouseDragged(TabInputContext ctx, int button) {
        return activeSection().mouseDragged(ctx, button);
    }

    @Override
    public boolean mouseReleased(TabInputContext ctx, int button) {
        return activeSection().mouseReleased(ctx, button);
    }

    @Override
    public boolean mouseScrolled(TabInputContext ctx, double scrollX, double scrollY) {
        return activeSection().mouseScrolled(ctx, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return activeSection().keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return activeSection().charTyped(codePoint, modifiers);
    }

    /** Every section, so switching to one shows what the stage holds rather than an empty list. */
    @Override
    public void load(StageEntry stage) {
        for (Section section : sections) section.tab().load(stage);
    }

    /** Every section — see the note on the class. This is the one that loses data if narrowed. */
    @Override
    public void store(StageEntry stage) {
        for (Section section : sections) section.tab().store(stage);
    }

    /** Every section: the host rebuilds pickers on resize, and a hidden one is shown again later. */
    @Override
    public void rebuildPicker() {
        for (Section section : sections) section.tab().rebuildPicker();
    }

    @Override
    @Nullable
    public PickerOverlay activeOverlay() {
        return activeSection().activeOverlay();
    }

    @Override
    public void openPicker(int centerX, int centerY, int parentWidth) {
        activeSection().openPicker(centerX, centerY, parentWidth);
    }
}
