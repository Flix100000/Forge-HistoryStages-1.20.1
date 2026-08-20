package net.bananemdnsa.historystages.client.editor.tab;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once on the client so an addon can give its lock category a tab in the stage editor.
 *
 * <p>Separate from the event that registers the category itself: that one is common-side, because
 * the server gates with it, while a tab is pure UI. Registering a category without an editor is
 * fine and means exactly what it looks like — the category works, it just cannot be edited in
 * game.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterCategoryEditorsEvent.class, event -> event.register(
 *         CategoryEditor.ofIdList("mymod:villagertrades",
 *                 "editor.mymod.search.villagertrades",
 *                 MyTrades::allKnownTradeIds)));
 * }</pre>
 */
public class RegisterCategoryEditorsEvent extends Event implements IModBusEvent {

    public void register(CategoryEditor editor) {
        CategoryEditors.register(editor);
    }
}
