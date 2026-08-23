package net.bananemdnsa.historystages.client.editor.tab;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * One extra entry in a tab row's right-click menu, declared by whoever owns the tab.
 *
 * <p>The built-in menus were assembled inside the editor screens, in an if-chain on the category
 * id, so an addon could offer nothing of its own beyond copy and remove. A declared action is how
 * it offers something.
 *
 * <p>Takes the row index rather than the entry: the tab already owns its rows, and passing a typed
 * entry would mean threading that type through every declaration for nothing — the addon closes
 * over its own tab and looks the entry up itself.
 *
 * <p>Client-side, like everything else that draws. A category or requirement is declared on the
 * common side, where the server reads it; its menu is not.
 */
public interface EntryAction {

    /** Lang key for the menu row. */
    String langKey();

    /**
     * Runs the action.
     *
     * @param index     the row that was right-clicked
     * @param onChanged call after changing anything, so the editor knows the stage is dirty
     */
    void run(int index, Runnable onChanged);

    static EntryAction of(String langKey, BiConsumer<Integer, Runnable> handler) {
        Objects.requireNonNull(langKey, "langKey");
        Objects.requireNonNull(handler, "handler");
        return new EntryAction() {
            @Override
            public String langKey() {
                return langKey;
            }

            @Override
            public void run(int index, Runnable onChanged) {
                handler.accept(index, onChanged);
            }
        };
    }
}
