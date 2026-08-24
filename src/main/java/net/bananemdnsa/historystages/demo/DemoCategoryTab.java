package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.api.editor.StringListCategoryTab;
import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.minecraft.network.chat.Component;

/**
 * The stand-in addon's lock-category tab, drawing itself.
 *
 * <p>Its twin on the dependency side gets more attention, but this one is the proof that matters
 * for the <em>lock</em> axis: the stage editor honours the same hooks, so an addon writes a tab the
 * same way whichever axis it plugs into. Without something like this the hook is only claimed to
 * work there.
 *
 * <p>What it shows that a plain row list cannot: taller rows, a colour block painted per entry, and
 * a button inside the row that moves the entry up. That last one is real editing and not a
 * decoration — the order of a category's entries is stored, so the button changes the stage file.
 *
 * <p>Reading and writing are inherited untouched from {@link StringListCategoryTab}. An addon that
 * only wants the list keeps {@code CategoryEditor.ofIdList} and writes none of this.
 */
final class DemoCategoryTab extends StringListCategoryTab {

    private static final int ROW_HEIGHT = 30;

    private final EditorRowList rows = new EditorRowList(ROW_HEIGHT);

    DemoCategoryTab(LockCategory<String> category, PickerFactory pickerFactory, Runnable onChanged) {
        super(category, pickerFactory, onChanged);
    }

    @Override
    public void onShown() {
        rows.resetSlideIn();
    }

    @Override
    public int contentHeight(int width) {
        return rows.heightForRows(entries().size());
    }

    @Override
    public int rowAt(TabInputContext ctx) {
        return rows.rowAt(ctx, entries().size());
    }

    @Override
    public boolean renderContent(TabRenderContext ctx) {
        rows.render(ctx, entries().size(), (row, i) -> {
            String relic = entries().get(i);
            row.leading(10, (g, x, y, w, h) -> g.fill(x, y + 2, x + w, y + h - 2, colourFor(relic)));
            row.text(relic);
            row.badge("#" + (i + 1), 0x888888);
            if (i > 0) {
                row.button(Component.translatable("editor.historystages.demo.row.move_up").getString(),
                        () -> moveUp(i));
            }
        });
        return true;
    }

    @Override
    public boolean mouseClicked(TabInputContext ctx, int button) {
        return button == 0 && rows.mouseClicked(ctx);
    }

    private void moveUp(int index) {
        if (index <= 0 || index >= entries().size()) return;
        String moved = entries().remove(index);
        entries().add(index - 1, moved);
        markChanged();
    }

    /** A stable colour per id, so the same relic looks the same every time the screen opens. */
    private static int colourFor(String relic) {
        int hash = relic.hashCode();
        return 0xFF000000 | (0x404040 + (hash & 0x7F7F7F));
    }
}
